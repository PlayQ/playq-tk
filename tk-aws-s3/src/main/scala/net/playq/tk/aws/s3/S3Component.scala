package net.playq.tk.aws.s3

import izumi.functional.bio.{Async2, BlockingIO2, F, Panic2}
import net.playq.tk.metrics.{MacroMetricS3Meter, MacroMetricS3Timer, Metrics}
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner

import java.util.concurrent.{CompletionStage, ThreadPoolExecutor}

trait S3Component[F[+_, +_]] {
  def componentIdentifier: String
  def executorService: ThreadPoolExecutor

  def presign[A](
    metric: String
  )(f: S3Presigner => A
  )(implicit
    saveCounter: MacroMetricS3Meter[metric.type],
    saveTimer: MacroMetricS3Timer[metric.type],
  ): F[Throwable, A]

  private[s3] def rawRequestF[E, A](
    metric: String
  )(f: S3AsyncClient => F[E, A]
  )(implicit
    saveCounter: MacroMetricS3Meter[metric.type],
    saveTimer: MacroMetricS3Timer[metric.type],
  ): F[E, A]

  def rawRequest[A](
    metric: String
  )(f: S3AsyncClient => CompletionStage[A]
  )(implicit
    saveCounter: MacroMetricS3Meter[metric.type],
    saveTimer: MacroMetricS3Timer[metric.type],
  ): F[Throwable, A]

  def getBucketRegion(bucketName: String): F[Throwable, String]
}

object S3Component {

  final class Impl[F[+_, +_]: Async2: BlockingIO2](
    region: Option[String],
    credentials: AwsCredentials,
    metrics: Metrics[F],
    client: S3AsyncClient,
    presigner: S3Presigner,
    blockingExecutor: ThreadPoolExecutor,
  ) extends S3Component[F] {

    override def executorService: ThreadPoolExecutor = blockingExecutor

    override def componentIdentifier: String = {
      val credentialsHash = s"${credentials.accessKeyId().hashCode}:${credentials.secretAccessKey().hashCode}"
      s"${region.getOrElse("unknown")}:$credentialsHash"
    }

    override def presign[A](
      metric: String
    )(f: S3Presigner => A
    )(implicit
      saveCounter: MacroMetricS3Meter[metric.type],
      saveTimer: MacroMetricS3Timer[metric.type],
    ): F[Throwable, A] = {
      F.shiftBlocking {
        metrics.timerAnd(metric) {
          F.syncThrowable(f(presigner)).tapError(_ => metrics.mark(metric))
        }
      }
    }

    override def rawRequestF[E, A](
      metric: String
    )(f: S3AsyncClient => F[E, A]
    )(implicit
      saveCounter: MacroMetricS3Meter[metric.type],
      saveTimer: MacroMetricS3Timer[metric.type],
    ): F[E, A] = {
      // we don't need to shift this execution
      // s3 request are already executes in blocking poll executor
      metrics.timerAnd(metric) {
        f(client).tapError(_ => metrics.mark(metric))
      }
    }

    override def rawRequest[A](
      metric: String
    )(f: S3AsyncClient => CompletionStage[A]
    )(implicit
      saveCounter: MacroMetricS3Meter[metric.type],
      saveTimer: MacroMetricS3Timer[metric.type],
    ): F[Throwable, A] = {
      rawRequestF(metric)(client => F.fromFutureJava(f(client)))
    }

    override def getBucketRegion(bucketName: String): F[Throwable, String] = {
      for {
        rsp <- rawRequest("get-bucket-region")(_.getBucketLocation(GetBucketLocationRequest.builder().bucket(bucketName).build()))
      } yield {
        Option(rsp.locationConstraint.toString)
          .filter(s => s != "null" && s.nonEmpty)
          .fold(Region.US_EAST_1)(Region.of)
          .toString
      }
    }
  }

  // format: off
  final class Dummy[F[+_, +_]: Panic2] extends S3Component[F] {
    override def componentIdentifier: String = s"dummy:${this.hashCode()}"
    override def executorService: ThreadPoolExecutor = throw new UnsupportedOperationException("Executor service is not support by dummy factory.")
    private val error = F.terminate(new UnsupportedOperationException("Dummy S3 component in use. Must be a bug."))
    override def presign[A](metric: String)(f: S3Presigner => A)(implicit saveCounter: MacroMetricS3Meter[metric.type], saveTimer: MacroMetricS3Timer[metric.type]): F[Throwable, A] = error
    override def rawRequestF[E, A](metric: String)(f: S3AsyncClient => F[E, A])(implicit saveCounter: MacroMetricS3Meter[metric.type], saveTimer: MacroMetricS3Timer[metric.type]): F[E, A] = error
    override def rawRequest[A](metric: String)(f: S3AsyncClient => CompletionStage[A])(implicit saveCounter: MacroMetricS3Meter[metric.type], saveTimer: MacroMetricS3Timer[metric.type]): F[Throwable, A] = error
    override def getBucketRegion(bucketName: String): F[Nothing, String] = F.pure("dummy")
  }
  // format: on

}
