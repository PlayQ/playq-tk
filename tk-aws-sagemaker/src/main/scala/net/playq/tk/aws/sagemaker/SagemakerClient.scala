package net.playq.tk.aws.sagemaker

import cats.implicits.{catsSyntaxBitraverse, toTraverseOps}
import com.github.dockerjava.core.util.CompressArchiveUtil
import izumi.distage.model.definition.{Lifecycle, Lifecycle2}
import izumi.functional.bio.catz.BIOToApplicative
import izumi.functional.bio.{BlockingIO2, Entropy2, F, IO2, Temporal2}
import izumi.fundamentals.collections.nonempty.NonEmptyList
import izumi.fundamentals.platform.language.Quirks.Discarder
import logstage.LogIO2
import logstage.LogIO2.log
import net.playq.tk.metrics.Metrics
import net.playq.tk.aws.s3.S3Bucket
import net.playq.tk.aws.s3.models.S3FileFormat.TARGZIP
import net.playq.tk.aws.s3.models.{S3File, S3FileFormat, S3Record, S3UploadContent}
import net.playq.tk.aws.sagemaker.SagemakerClient.S3URI
import net.playq.tk.aws.sagemaker.TrainingImageProvider.TrainingImageURI
import net.playq.tk.aws.sagemaker.config.SagemakerConfig
import net.playq.tk.aws.sagemaker.model.TrainingScript.SourceFile
import net.playq.tk.aws.sagemaker.model.{TrainingHardware, TrainingJobName, TrainingScript}
import net.playq.tk.metrics.{MacroMetricSagemakerMeter, MacroMetricSagemakerTimer}
import net.playq.tk.util.ManagedFile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sagemaker.SageMakerClient
import software.amazon.awssdk.services.sagemaker.model.*

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.chaining.scalaUtilChainingOps

trait SagemakerClient[F[+_, +_]] {
  def startTrainingJob(
    trainingScript: TrainingScript,
    trainingImage: TrainingImageURI,
    trainingHardware: TrainingHardware,
    bucket: S3Bucket[F, ?],
    trainingFacts: Seq[Either[S3URI, File]],
  ): F[Throwable, TrainingJobName]

  def awaitTrainingJob(trainingJobName: TrainingJobName): F[Throwable, S3URI]

  def rawRequest[A](
    metric: String
  )(f: SageMakerClient => A
  )(implicit
    saveCounter: MacroMetricSagemakerMeter[metric.type],
    saveTimer: MacroMetricSagemakerTimer[metric.type],
  ): F[Throwable, A]
}

object SagemakerClient {

  final class Impl[F[+_, +_]: IO2: BlockingIO2: Temporal2: LogIO2](
    metrics: Metrics[F],
    entropy: Entropy2[F],
    config: SagemakerConfig,
  ) extends Lifecycle.Of[F[Throwable, _], SagemakerClient[F]](inner = for {
      client <- Lifecycle.fromAutoCloseable(F.syncThrowable {
        SageMakerClient
          .builder()
          .pipe(builder => config.getRegion.fold(builder)(builder region Region.of(_)))
          .build()
      })
    } yield new SagemakerClient[F] {

      override def startTrainingJob(
        trainingScript: TrainingScript,
        trainingImage: TrainingImageURI,
        trainingHardware: TrainingHardware,
        bucket: S3Bucket[F, ?],
        trainingFacts: Seq[Either[S3URI, File]],
      ): F[Throwable, TrainingJobName] = {
        for {
          trainingJobName <- entropy.nextUUID().map(uuid => s"ml-training-${uuid.toString.takeRight(12)}")
          uploadedSources <- uploadSources(bucket, trainingJobName)(trainingScript)
          mbUploadedFacts <- trainingFacts.traverse(
            _.bitraverse(
              F pure mkChannel(_),
              uploadFacts(bucket, trainingJobName),
            ).map(_.merge)
          )
          _ <- rawRequest("create-training-job")(
            _.createTrainingJob(
              CreateTrainingJobRequest
                .builder()
                .algorithmSpecification(
                  AlgorithmSpecification
                    .builder()
                    .trainingImage(trainingImage.uri)
                    .trainingInputMode(TrainingInputMode.FILE)
                    .build()
                )
                .pipe(b => NonEmptyList.from(mbUploadedFacts).fold(b)(channels => b.inputDataConfig(channels.toList: _*)))
                .outputDataConfig(
                  OutputDataConfig
                    .builder()
                    .s3OutputPath(s"s3://${bucket.name}/")
                    .kmsKeyId(null)
                    .build()
                )
                .trainingJobName(trainingJobName)
                .stoppingCondition(
                  StoppingCondition.builder().maxRuntimeInSeconds(60 * 60 * 24).build()
                )
                .resourceConfig(
                  ResourceConfig
                    .builder()
                    .instanceType(trainingHardware.instanceType)
                    .instanceCount(trainingHardware.instanceCount)
                    .volumeSizeInGB(trainingHardware.volumeSizeInGB)
                    .build()
                )
                .roleArn(config.roleArn)
                .hyperParameters {
                  def quote(s: String): String = "\"" + s + "\""

                  Map(
                    "model_dir"                     -> quote(s"s3://${bucket.name}/$trainingJobName/model"),
                    "sagemaker_container_log_level" -> "20",
                    "sagemaker_job_name"            -> quote(trainingJobName),
                    "sagemaker_program"             -> quote(uploadedSources.scriptFileName),
                    "sagemaker_region"              -> quote(config.regionOrDefault),
                    "sagemaker_submit_directory"    -> quote(S3URI.format(bucket, uploadedSources.s3File.fileName)),
                  ).asJava
                }
                .build()
            )
          )
        } yield TrainingJobName(trainingJobName)
      }

      override def awaitTrainingJob(trainingJobName: TrainingJobName): F[Throwable, S3URI] = {
        log.info(s"Checking training job $trainingJobName") *>
        rawRequest("describe-training-job")(
          _.describeTrainingJob(
            DescribeTrainingJobRequest
              .builder()
              .trainingJobName(trainingJobName.trainingJobName)
              .build()
          )
        ).flatMap {
          res =>
            res.trainingJobStatus() match {
              case TrainingJobStatus.COMPLETED =>
                F.pure(S3URI(res.modelArtifacts().s3ModelArtifacts()))

              case status @ (TrainingJobStatus.FAILED | TrainingJobStatus.STOPPED) =>
                log.fail(
                  s"Job Halted with $status: ${res.roleArn() -> "roleArn"}  ${res.stoppingCondition() -> "stoppingCondition"} ${res.failureReason() -> "failureReason"}"
                )

              case status @ (TrainingJobStatus.IN_PROGRESS | TrainingJobStatus.STOPPING | TrainingJobStatus.UNKNOWN_TO_SDK_VERSION) =>
                val duration = 10.seconds
                log.info(s"Got $status, waiting $duration for $trainingJobName") *>
                F.sleep(duration) *> awaitTrainingJob(trainingJobName)
            }
        }
      }

      override def rawRequest[A](
        metric: String
      )(f: SageMakerClient => A
      )(implicit saveCounter: MacroMetricSagemakerMeter[metric.type],
        saveTimer: MacroMetricSagemakerTimer[metric.type],
      ): F[Throwable, A] = {
        F.shiftBlocking {
          metrics.withTimer(metric) {
            F.syncThrowable(f(client)).catchAll {
              failure =>
                metrics.mark(metric)(saveCounter) *> F.fail(failure)
            }
          }(saveTimer)
        }
      }

      private[this] def uploadFacts(bucket: S3Bucket[F, ?], trainingJobName: String)(facts: File): F[Throwable, Channel] = {
        val outputFile = s"$trainingJobName/training/${facts.getName}"
        val s3File     = S3File(outputFile, S3FileFormat.ORC)

        for {
          _      <- bucket.upload(S3Record(s3File, S3UploadContent.File(facts)))
          channel = mkChannel(S3URI(bucket, outputFile))
        } yield channel
      }

      private[this] def mkChannel(s3URI: S3URI) = {
        Channel
          .builder()
          .channelName("training")
          .dataSource(
            DataSource
              .builder()
              .s3DataSource(
                S3DataSource
                  .builder()
                  .s3Uri(s3URI.s3Uri)
                  .s3DataType(S3DataType.S3_PREFIX)
                  .build()
              )
              .build()
          )
          .build()
      }

      private[this] def uploadSources(bucket: S3Bucket[F, ?], trainingJobName: String)(trainingScript: TrainingScript): F[Throwable, UploadedSources] = {
        gzippedSourceArchive(trainingScript).use {
          case (tarFile, scriptFileName) =>
            val outputFile = s"$trainingJobName/source.tar.gz"
            val s3File     = S3File(outputFile, TARGZIP)
            for {
              _ <- bucket.upload(S3Record(s3File, S3UploadContent.File(tarFile.file)))
            } yield UploadedSources(s3File, scriptFileName)
        }
      }

      private[this] def gzippedSourceArchive(trainingScript: TrainingScript): Lifecycle2[F, Throwable, (ManagedFile, String)] = {
        for {
          sourceDir     <- writeSourceDir(trainingScript.sourceFiles)
          tarFile       <- tarGzSource(sourceDir.path)
          scriptFileName = trainingScript.sourceFiles.head.filename // assume that the first script file is the main file
        } yield (tarFile, scriptFileName)
      }

      private[this] def writeSourceDir(sourceFiles: NonEmptyList[SourceFile]): Lifecycle2[F, Throwable, ManagedFile] = {
        ManagedFile
          .managedDirectory("ml-training-script")
          .evalTap {
            sourceDir =>
              F.syncThrowable {
                sourceFiles.foreach {
                  sourceFile =>
                    Files.write(sourceDir.path.resolve(sourceFile.filename), sourceFile.sourceCode.getBytes(UTF_8), StandardOpenOption.CREATE).discard()
                }
              }
          }
      }

      private[this] def tarGzSource(scriptFile: Path): Lifecycle2[F, Throwable, ManagedFile] = {
        ManagedFile
          .managedFile("src-archive", ".tar.gz")
          .evalTap(tarFile => F.syncThrowable(CompressArchiveUtil.tar(scriptFile, tarFile.path, true, true)))
      }

    })

  final case class S3URI(s3Uri: String) {
    lazy val (bucket: String, key: String) = {
      "s3://(.+?)/(.*)".r.findFirstMatchIn(s3Uri).map(_.subgroups) match {
        case Some(List(bucketName, path)) => (bucketName, path)
        case _                            => throw new IllegalArgumentException(s"Not a valid S3 URI `$s3Uri`")
      }
    }
    override def toString: String = s3Uri
  }
  object S3URI {
    def apply[F[_, _]](bucket: S3Bucket[F, ?], key: String): S3URI = {
      S3URI(S3URI.format(bucket, key))
    }
    def format[F[_, _]](bucket: S3Bucket[F, ?], key: String): String = {
      s"s3://${bucket.name}/$key"
    }
  }

  private[this] final case class UploadedSources(s3File: S3File, scriptFileName: String)

}
