package net.playq.tk.aws.s3

import fs2.{Chunk, Stream}
import izumi.distage.model.definition.{Lifecycle, Lifecycle2}
import izumi.functional.bio.{Error2, F, IO2}
import izumi.fundamentals.platform.language.unused
import logstage.LogIO2
import net.playq.tk.aws.tagging.SharedTags
import net.playq.tk.aws.s3.config.S3Config
import net.playq.tk.aws.s3.health.S3HealthChecker
import net.playq.tk.aws.s3.models.*
import net.playq.tk.metrics.{MacroMetricS3Meter, MacroMetricS3Timer}
import net.playq.tk.quantified.SyncThrowable
import net.playq.tk.util.ManagedFile
import org.apache.commons.compress.utils.IOUtils
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.model.{GetObjectPresignRequest, PutObjectPresignRequest}

import java.net.{URL, URLEncoder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.chaining.*

trait S3BucketFactory[F[+_, +_]] {
  def mkClient[BucketId <: S3BucketId](bucketId: BucketId): Lifecycle2[F, Throwable, S3Bucket[F, BucketId]]
  def wrapClient[BucketId <: S3BucketId](bucketId: BucketId)(component: S3Component[F]): S3Bucket[F, BucketId]
}

object S3BucketFactory {

  final class Dummy[F[+_, +_]: IO2: SyncThrowable] extends S3BucketFactory[F] {
    private[this] val buckets = TrieMap.empty[String, S3Bucket[F, ?]]
    override def mkClient[BucketId <: S3BucketId](bucketId: BucketId): Lifecycle2[F, Throwable, S3Bucket[F, BucketId]] = Lifecycle.liftF(F.sync {
      buckets.getOrElseUpdate(bucketId.bucketName, new S3Bucket.Dummy[F, BucketId](bucketId)).asInstanceOf[S3Bucket[F, BucketId]]
    })
    override def wrapClient[BucketId <: S3BucketId](bucketId: BucketId)(component: S3Component[F]): S3Bucket[F, BucketId] = {
      buckets.getOrElseUpdate(bucketId.bucketName, new S3Bucket.Dummy[F, BucketId](bucketId)).asInstanceOf[S3Bucket[F, BucketId]]
    }
  }

  final class Impl[F[+_, +_]: IO2: SyncThrowable](
    generalClient: S3Component[F],
    clientFactory: S3ComponentFactory[F],
    config: S3Config,
    @unused healthChecker: S3HealthChecker[F],
  )(implicit
    log: LogIO2[F]
  ) extends S3BucketFactory[F] {

    override def mkClient[BucketId <: S3BucketId](bucketId: BucketId): Lifecycle2[F, Throwable, S3Bucket[F, BucketId]] = {
      (bucketId: S3BucketId) match {
        case g: S3BucketId.GenWithOverride if g.serviceCreatesBucket =>
          Lifecycle.suspend {
            for {
              _       <- log.info(s"For ${g.name -> "bucket"} will use ${bucketId.bucketName}")
              region  <- generalClient.getBucketRegion(bucketId.bucketName).logThrowable("GetBucketLocation", bucketId.bucketName)
              _       <- log.info(s"For ${bucketId.bucketName} will use client with $region")
              regional = clientFactory.mkClient(Some(region), None)
            } yield regional.map(wrapClient(bucketId))
          }.catchAll {
            _ =>
              Lifecycle.liftF(for {
                _ <- log.info(s"Bucket with ${bucketId.bucketName} was not found will create a new one.")
                _ <- generalClient
                  .rawRequest("create-bucket") {
                    _.createBucket(CreateBucketRequest.builder().bucket(bucketId.bucketName).build())
                  }.logThrowable("CreateBucket", bucketId.bucketName)
              } yield wrapClient(bucketId)(generalClient))
          }.evalMap {
            bucket =>
              for {
                tags     <- bucket.getBucketTags
                tagsToSet = g.tags
                _ <- F.when(!tags.forall { case (k, v) => tagsToSet.exists { case (k1, v1) => k1 == k && v1 == v } } || tags.isEmpty) {
                  log.info(
                    s"Old ${bucket.name -> "bucketName"} tags does not correspond to a generated one. Will update. \n${tags -> "oldTags"}\n${tagsToSet -> "newTags"}"
                  ) *>
                  bucket.setBucketTags(tagsToSet.map(S3Tag.toTag).toSeq: _*)
                }
              } yield bucket
          }

        case _ =>
          Lifecycle.suspend(for {
            region <-
              generalClient
                .getBucketRegion(bucketId.bucketName)
                .logThrowable("GetBucketLocation", bucketId.bucketName)
                .catchSome {
                  case _ if config.getEndpoint.nonEmpty =>
                    log.info(s"Found local env. Will create Static bucket. ${bucketId.bucketName}") *>
                    generalClient.rawRequest("create-bucket")(_.createBucket(CreateBucketRequest.builder().bucket(bucketId.bucketName).build)) *>
                    generalClient.getBucketRegion(bucketId.bucketName).logThrowable("GetBucketLocation", bucketId.bucketName)
                }
            _       <- log.info(s"For ${bucketId.bucketName} will use client with $region")
            regional = clientFactory.mkClient(Some(region), None)
          } yield {
            regional
              .map(wrapClient(bucketId))
              .evalTap(bucket => log.info(s"Using static bucket name: ${bucket.name -> "bucketName"}"))
          })
      }
    }

    override def wrapClient[BucketId <: S3BucketId](bucketId: BucketId)(component: S3Component[F]): S3Bucket[F, BucketId] = new S3Bucket[F, BucketId] {
      import bucketId.bucketName

      override val name: String = bucketName

      override def markObjectForDeletion(location: String): F[Throwable, Unit] = {
        val (k, v) = SharedTags.markedForDeletion
        val t      = Tag.builder.key(k).value(v).build
        _tagObject(location, t)
      }

      override def tagObject[T: S3Tag](location: String, tag: T): F[Throwable, Unit] = {
        _tagObject(location, S3Tag.toTag(tag))
      }

      override def getObjectTags(location: String): F[Throwable, Map[String, String]] = {
        component
          .rawRequest("get-object-tags") {
            client =>
              val old = client.getObjectTagging(GetObjectTaggingRequest.builder.bucket(name).key(location).build)
              old.tagSet.asScala.map(t => t.key -> t.value).toMap
          }.logThrowable("GetObjectTagging", bucketName)
      }

      private[this] def _tagObject(location: String, tag: Tag): F[Throwable, Unit] = {
        component
          .rawRequest("tag-object") {
            client =>
              val old     = client.getObjectTagging(GetObjectTaggingRequest.builder.bucket(name).key(location).build)
              val newTags = Tagging.builder.tagSet((tag :: old.tagSet.asScala.toList).asJava).build
              client.putObjectTagging(PutObjectTaggingRequest.builder.bucket(name).key(location).tagging(newTags).build)
          }.void.logThrowable("SetObjectTagging", bucketName)
      }

      override def getObjectPresigned(location: String, responseContentType: Option[String], responseContentDisposition: Option[String]): F[Throwable, URL] = {
        component
          .presign("generate-presigned-get") {
            presigner =>
              val getRequestBuilder = GetObjectRequest.builder.bucket(bucketName).key(location)
              val builderWithType   = responseContentType.fold(getRequestBuilder)(getRequestBuilder.responseContentType)
              val getRequest        = responseContentDisposition.fold(builderWithType)(builderWithType.responseContentDisposition).build
              val presignedGet      = GetObjectPresignRequest.builder.signatureDuration(java.time.Duration.ofMinutes(15)).getObjectRequest(getRequest).build
              presigner.presignGetObject(presignedGet).url
          }.logThrowable("GetObjectPresign", bucketName)
      }

      override def putObjectPresigned(location: String, contentType: Option[String], tags: Map[String, String]): F[Throwable, URL] = {
        component
          .presign("generate-presigned-put") {
            presigner =>
              val tagging            = if (tags.nonEmpty) Some(Tagging.builder.tagSet(tags.map(S3Tag.toTag).toSet.asJava).build) else None
              val putRequestBuilder  = PutObjectRequest.builder.bucket(bucketName).key(location)
              val builderWithTagging = tagging.fold(putRequestBuilder)(putRequestBuilder.tagging)
              val putRequest         = contentType.fold(builderWithTagging)(builderWithTagging.contentType).build
              val presignedPut       = PutObjectPresignRequest.builder.signatureDuration(java.time.Duration.ofMinutes(15)).putObjectRequest(putRequest).build
              presigner.presignPutObject(presignedPut).url
          }.logThrowable("PutObjectPresign", bucketName)
      }

      override def getBucketTags: F[Throwable, Map[String, String]] = {
        component
          .rawRequest("get-tags") {
            client =>
              Try(Option(client.getBucketTagging(GetBucketTaggingRequest.builder.bucket(bucketName).build))).toOption.flatten
                .fold(Map.empty[String, String])(_.tagSet.asScala.map(t => t.key -> t.value).toMap)
          }.logThrowable("GetBucketTaggingConfiguration", bucketName)
      }

      override def setBucketTags(tags: Tag*): F[Throwable, Unit] = {
        component
          .rawRequest("set-tags") {
            client =>
              val tagging = Tagging.builder.tagSet(tags.asJava).build
              client.putBucketTagging(PutBucketTaggingRequest.builder.bucket(bucketName).tagging(tagging).build)
          }.logThrowable("SetBucketTaggingConfiguration", bucketName).void
      }

      override def healthProbe: F[Throwable, Unit] = {
        component
          .rawRequest("health-probe") {
            _.listObjects(ListObjectsRequest.builder.bucket(bucketName).prefix(".").maxKeys(1).build)
          }.void
      }

      override def getArn: F[Throwable, String] = {
        F.pure(s"arn:aws:s3:*:*:$bucketName")
      }

      override def configureSQSNotifications(sqsArn: String, events: Set[Event]): F[Throwable, Unit] = {
        component.rawRequestF("configure-sqs-notifications") {
          client =>
            for {
              configs <- F.syncThrowable {
                client.getBucketNotificationConfiguration(GetBucketNotificationConfigurationRequest.builder().bucket(name).build())
              }.logThrowable("GetBucketNotificationConfiguration", bucketName)
              oldConfigs = configs.queueConfigurations().asScala.toList
              _ <-
                if (oldConfigs.exists(c => c.queueArn() == sqsArn && c.events().asScala.toSet.diff(events).isEmpty)) {
                  log.info(s"$bucketName already configured to notify $sqsArn.")
                } else {
                  val newConf = PutBucketNotificationConfigurationRequest
                    .builder()
                    .bucket(bucketName)
                    .notificationConfiguration(
                      NotificationConfiguration
                        .builder().queueConfigurations(QueueConfiguration.builder.queueArn(sqsArn).events(events.asJavaCollection).id("sqsQueueConfig").build()).build()
                    ).build()
                  log.info(s"Updating $bucketName notification config to use $sqsArn.") *>
                  F.syncThrowable(client.putBucketNotificationConfiguration(newConf)).logThrowable("SetBucketNotificationConfiguration", bucketName)
                }
            } yield ()
        }
      }

      override def upload(record: S3Record[S3UploadContent]): F[Throwable, Unit] = {
        objectOp("PutObject", record.file.fileName, "uploading")(uploadImpl(record)).void
      }

      override def download(key: String): F[Throwable, S3DownloadResponse] = {
        objectOp("GetObject", key, "downloading")(downloadImpl(key))
      }

      override def downloadToFile(key: String, path: Option[Path]): Lifecycle[F[Throwable, _], ManagedFile] = {
        Lifecycle.fromAutoCloseable {
          objectOp("GetObject", key, "downloading")(downloadToFileImpl(key, path))
        }
      }

      override def doesObjectExists(key: String): F[Throwable, Boolean] = {
        component
          .rawRequest("object-exist") {
            client =>
              try {
                client.getObjectTagging(GetObjectTaggingRequest.builder.bucket(bucketName).key(key).build)
                true
              } catch {
                case _: NoSuchKeyException => false
                case e: Throwable          => throw e
              }
          }.logThrowable("GetObjectMetadata", bucketName)
      }

      override def delete(key: String): F[Throwable, Unit] = {
        objectOp("DeleteObject", key, "deleting")(deleteImpl(key))
      }

      override def listObjects(prefix: String): F[Throwable, List[String]] = {
        streamObjects(prefix).compile.toList
      }

      override def listObjectsMeta(prefix: String): F[Throwable, List[S3ObjectMeta]] = {
        streamObjectsMeta(prefix).compile.toList
      }

      override def copyObject(sourceKey: String, targetBucket: String, targetKey: String): F[Throwable, Unit] = {
        objectOp("CopyingObject", bucketName, "copy-object")(copyObjectImpl(bucketName, sourceKey, targetBucket, targetKey))
      }

      override def copyObject(sourceKey: String, targetKey: String): F[Throwable, Unit] = {
        copyObject(sourceKey, bucketName, targetKey)
      }

      override def streamObjects(prefix: String): Stream[F[Throwable, _], String] = {
        streamObjectsImpl(prefix, "list-bucket-prefix-ops")(_.key)
      }

      override def streamObjectsMeta(prefix: String): Stream[F[Throwable, _], S3ObjectMeta] = {
        streamObjectsImpl(prefix, "list-bucket-prefix-ops")(toMeta(bucketName))
      }

      private[this] def objectOp[R](
        opName: String,
        objKey: String,
        metric: String,
      )(cont: S3Client => F[Throwable, R]
      )(implicit
        saveCounter: MacroMetricS3Meter[metric.type],
        saveTimer: MacroMetricS3Timer[metric.type],
      ): F[Throwable, R] = {
        component
          .rawRequestF(metric) {
            client =>
              for {
                thread <- F.sync(Thread.currentThread().getName)
                _      <- log.debug(s"$opName to S3 started on $thread for ${objKey -> "file"} $bucketName")
                res    <- cont(client)
              } yield res
          }.logThrowable(opName, bucketName)
      }

      private[this] def uploadImpl(record: S3Record[S3UploadContent])(client: S3Client): F[Throwable, PutObjectResponse] = {
        F.syncThrowable {
          val fileName = record.file.fileName
          val (request, body) = record.content.toRequest(bucketName, fileName)(
            _.contentType(record.file.format.contentType).pipe(b => record.cacheControl.fold(b)(b.cacheControl))
          )

          client.putObject(request, body)
        }
      }

      private[this] def downloadImpl(key: String)(client: S3Client): F[Throwable, S3DownloadResponse] = {
        F.syncThrowable {
          DownloadRequest(client.getObject(GetObjectRequest.builder.bucket(bucketName).key(key).build()))
        }.bracketAuto {
          response =>
            F.syncThrowable(IOUtils.toByteArray(response.content)).map(S3DownloadResponse(_, response.content.response.contentType))
        }
      }

      private[this] def downloadToFileImpl(key: String, path: Option[Path])(client: S3Client): F[Throwable, ManagedFile] = {
        F.syncThrowable {
          DownloadRequest(client.getObject(GetObjectRequest.builder.bucket(bucketName).key(key).build()))
        }.bracketAuto {
          response =>
            F.syncThrowable {
              val file = path.fold {
                key
                  .split("/").lastOption
                  .map(full => full.splitAt(full.lastIndexOf(".")))
                  .fold(ManagedFile.createFile()) { case (p, s) => ManagedFile.createFile(p, s) }
              }(ManagedFile.managedExternal)
              Files.copy(response.content, file.path, StandardCopyOption.REPLACE_EXISTING)
              file
            }
        }
      }

      private[this] def deleteImpl(key: String)(client: S3Client): F[Throwable, Unit] = {
        val request = DeleteObjectRequest.builder().bucket(bucketName).key(key).build()
        F.syncThrowable(client.deleteObject(request)).void
      }

      private[this] def copyObjectImpl(sourceBucket: String, sourceKey: String, targetBucket: String, targetKey: String)(client: S3Client): F[Throwable, Unit] = {
        val url     = URLEncoder.encode(sourceBucket + "/" + sourceKey, StandardCharsets.UTF_8)
        val request = CopyObjectRequest.builder().copySource(url).destinationBucket(targetBucket).destinationKey(targetKey).build()
        F.syncThrowable(client.copyObject(request)).void
      }

      private[this] def streamObjectsImpl[A](
        prefix: String,
        metric: String,
      )(f: S3Object => A
      )(implicit
        saveCounter: MacroMetricS3Meter[metric.type],
        saveTimer: MacroMetricS3Timer[metric.type],
      ): Stream[F[Throwable, _], A] = {
        val request = ListObjectsRequest.builder().bucket(bucketName).prefix(prefix).build()
        Stream.eval {
          objectOp("List first page", prefix, metric) {
            client =>
              F.syncThrowable(client.listObjects(request)).logThrowable("ListObjects", bucketName)
          }
        }.flatMap {
          Stream.unfoldLoopEval(_) {
            currentResponse =>
              val listRes = currentResponse.contents().asScala.toList.map(f)
              if (currentResponse.isTruncated) {
                objectOp("List next page", prefix, metric) {
                  client =>
                    F.syncThrowable(client.listObjects(request.toBuilder.marker(currentResponse.nextMarker()).build())).logThrowable("ListNextBatchOfObjects", bucketName)
                }.map(listRes -> Some(_))
              } else {
                F.pure(listRes -> None): F[Throwable, (List[A], Option[ListObjectsResponse])]
              }
          }
        }.mapChunks(_.flatMap(Chunk.seq))
      }

      private[this] def toMeta(bucket: String)(summary: S3Object) =
        S3ObjectMeta(
          bucketName = bucket,
          key        = summary.key,
          eTag       = summary.eTag,
          size       = summary.size,
          modifiedAt = summary.lastModified.toEpochMilli,
        )
    }
  }

  private final case class DownloadRequest(content: ResponseInputStream[GetObjectResponse]) extends AutoCloseable {
    override def close(): Unit = content.close()
  }

  private implicit final class ThrowableS3Ops[F[+_, +_], A](private val f: F[Throwable, A]) extends AnyVal {
    def logThrowable(operation: String, bucketName: String)(implicit F: Error2[F], log: LogIO2[F]): F[Throwable, A] = {
      f.tapError {
        case _: NoSuchKeyException => F.unit
        case failure               => log.error(s"S3: Got error during executing $operation for $bucketName. ${failure.getMessage -> "Failure"}.")
      }
    }
  }

}
