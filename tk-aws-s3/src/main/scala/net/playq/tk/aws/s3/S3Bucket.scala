package net.playq.tk.aws.s3

import distage.Id
import fs2.{Chunk, Stream}
import izumi.distage.constructors.ClassConstructor
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.functional.bio.{Async2, Clock1, Error2, Exit, F, IO2}
import izumi.fundamentals.platform.language.Quirks.*
import logstage.LogIO2
import net.playq.tk.aws.s3.S3Bucket.{CorsRule, PresignedPut}
import net.playq.tk.aws.s3.health.S3HealthChecker
import net.playq.tk.aws.s3.models.*
import net.playq.tk.aws.tagging.SharedTags
import net.playq.tk.metrics.{MacroMetricS3Meter, MacroMetricS3Timer}
import net.playq.tk.quantified.AsyncThrowable
import net.playq.tk.util.ClosableFile
import software.amazon.awssdk.core.FileTransformerConfiguration
import software.amazon.awssdk.core.FileTransformerConfiguration.{FailureBehavior, FileWriteOption}
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.model.{GetObjectPresignRequest, PutObjectPresignRequest}
import software.amazon.awssdk.services.s3.{S3AsyncClient, model}

import java.net.{URI, URL}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.ZonedDateTime
import java.util.Base64
import scala.annotation.unused
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

trait S3Bucket[F[_, _], +BucketId <: S3BucketId] {
  val bucket: BucketId
  val bucketName: String
  val bucketIdentifier: String

  def upload(record: S3Record[S3UploadContent]): F[Throwable, Unit]
  def meta(key: String): F[Throwable, Option[S3ObjectMeta]]

  def download(key: String): F[Throwable, Option[S3DownloadResponse]]
  def downloadToFile(key: String, path: Option[Path] = None): Lifecycle[F[Throwable, _], Option[ClosableFile]]

  def doesObjectExists(key: String): F[Throwable, Boolean]
  def delete(key: String): F[Throwable, Unit]
  def deleteBatch(keys: List[String]): F[Throwable, Unit]
  def markObjectForDeletion(key: String): F[Throwable, Unit]

  def listObjects(prefix: String): F[Throwable, List[String]]
  def listObjectsMeta(prefix: String): F[Throwable, List[S3ObjectMeta]]
  def listFolders(prefix: String, delimiter: Char = '/'): F[Throwable, List[String]]

  def streamObjects(prefix: String): Stream[F[Throwable, _], String]
  def streamObjectsMeta(prefix: String): Stream[F[Throwable, _], S3ObjectMeta]
  def streamFolders(prefix: String, delimiter: Char = '/'): Stream[F[Throwable, _], String]

  def copyObject(sourceKey: String, targetBucket: String, targetKey: String): F[Throwable, Unit]
  def copyObject(sourceKey: String, targetKey: String): F[Throwable, Unit]

  def getObjectPresigned(
    location: String,
    responseContentType: Option[String],
    responseContentDisposition: Option[String],
    responseCacheControl: Option[String],
  ): F[Throwable, URL]

  def putObjectPresigned(
    location: String,
    contentType: Option[String],
    sha256B64: Option[String] = None,
    tags: List[Tag]           = Nil,
  ): F[Throwable, PresignedPut]

  def tagObject[T: S3Tag](location: String, tag: T): F[Throwable, Unit]
  def getObjectTags(location: String): F[Throwable, Map[String, String]]

  def getArn: F[Throwable, String]
  def getBucketTags: F[Throwable, Map[String, String]]
  def setBucketTags(tags: Seq[model.Tag]): F[Throwable, Unit]
  def configureSQSNotifications(sqsArn: String, events: Set[Event]): F[Throwable, Unit]
  def healthProbe: F[Throwable, Unit]
  def getBucketCors: F[Throwable, Set[CorsRule]]
  def setBucketCors(rules: Set[CorsRule]): F[Throwable, Unit]

  override final def toString: String = bucketName
}

object S3Bucket {

  final case class PresignedPut(url: URL, headers: Map[String, List[String]])
  final case class CorsRule(methods: Set[String], headers: Set[String], origins: Set[String], exposeHeaders: Set[String], maxAgeSeconds: Int) {
    def toCORSRule: CORSRule = {
      CORSRule
        .builder()
        .pipe(b => if (methods.nonEmpty) b.allowedMethods(methods.asJavaCollection) else b)
        .pipe(b => if (headers.nonEmpty) b.allowedHeaders(headers.asJavaCollection) else b)
        .pipe(b => if (origins.nonEmpty) b.allowedOrigins(origins.asJavaCollection) else b)
        .pipe(b => if (exposeHeaders.nonEmpty) b.exposeHeaders(exposeHeaders.asJavaCollection) else b)
        .maxAgeSeconds(maxAgeSeconds)
        .build()
    }
  }
  object CorsRule {
    def putAllowAll: CorsRule = CorsRule(
      methods       = Set("PUT"),
      headers       = Set("*"),
      origins       = Set("*"),
      exposeHeaders = Set.empty,
      maxAgeSeconds = 3000,
    )
    def getAllowAll: CorsRule = CorsRule(
      methods       = Set("GET"),
      headers       = Set("*"),
      origins       = Set("*"),
      exposeHeaders = Set.empty,
      maxAgeSeconds = 3000,
    )
  }

  def makeBucket[F[+_, +_]: distage.TagKK, BucketId <: S3BucketId: distage.Tag: ClassConstructor](implicit mutCtx: ModuleDef#MutationContext): Unit = new mutCtx.dsl {
    make[BucketId]
    // use proxy here to silently bind S3HealthChecker to a bucket
    make[S3Bucket[F, BucketId]].named("proxy").fromResource((_: S3BucketFactory[F]).mkClient(_: BucketId))
    make[S3Bucket[F, BucketId]].from {
      (proxyBucket: S3Bucket[F, BucketId] @Id("proxy"), _: S3HealthChecker[F]) =>
        proxyBucket
    }
    many[S3Bucket[F, S3BucketId]].weak[S3Bucket[F, BucketId]]("proxy")
  }.discard()

  def makeBucketCached[F[+_, +_]: distage.TagKK, BucketId <: S3BucketId: distage.Tag: ClassConstructor](implicit mutCtx: ModuleDef#MutationContext): Unit =
    new mutCtx.dsl {
      makeBucket[F, BucketId]
      make[S3BucketCached[F, BucketId]].from[S3BucketCached.FromS3Cache[F, BucketId]]
    }.discard()

  final class Dummy[F[+_, +_]: IO2, BucketId <: S3BucketId](bucketId: BucketId) extends S3Bucket[F, BucketId] {
    private[this] val content = TrieMap.empty[String, (S3Record[S3UploadContent], ZonedDateTime)]

    override val bucket: BucketId         = bucketId
    override val bucketName: String       = bucketId.bucketName
    override val bucketIdentifier: String = s"dummy:${bucketId.bucketName}"

    override def upload(record: S3Record[S3UploadContent]): F[Throwable, Unit] = F.sync(synchronized {
      val contentWithoutFiles = record.content match {
        case S3UploadContent.File(file, tags) => S3UploadContent.Bytes(Files.readAllBytes(file.toPath), tags)
        case other                            => other
      }
      content.addOne(record.file.fileName -> (record.copy(content = contentWithoutFiles) -> Clock1.Standard.nowZoned())).discard()
    })

    override def download(key: String): F[Throwable, Option[S3DownloadResponse]] = {
      downloadImpl(key).map(_.map(_._1))
    }

    private[this] def downloadImpl(key: String): F[Throwable, Option[(S3DownloadResponse, ZonedDateTime)]] = F.syncThrowable(synchronized {
      content.get(key).map {
        case (c, z) =>
          val rsp = c.content match {
            case S3UploadContent.Bytes(bytes, _)         => S3DownloadResponse(bytes, c.file.format.contentType, z.toInstant)
            case S3UploadContent.File(file, _)           => S3DownloadResponse(Files.readAllBytes(file.toPath), c.file.format.contentType, z.toInstant)
            case S3UploadContent.String(string, _)       => S3DownloadResponse(string.getBytes(), c.file.format.contentType, z.toInstant)
            case S3UploadContent.Stream(stream, _, _, _) => S3DownloadResponse(stream.readAllBytes(), c.file.format.contentType, z.toInstant)
            case json: S3UploadContent.Json[?]           => S3DownloadResponse(json.stringBody.getBytes(), c.file.format.contentType, z.toInstant)
          }
          rsp -> z
      }
    })

    override def meta(key: String): F[Throwable, Option[S3ObjectMeta]] = {
      downloadImpl(key).map(
        _.map {
          case (rsp, at) =>
            models.S3ObjectMeta(
              bucketName = bucketName,
              key        = key,
              eTag       = "test",
              size       = rsp.content.length.toLong,
              modifiedAt = at.toInstant,
            )
        }
      )
    }

    override def downloadToFile(key: String, path: Option[Path]): Lifecycle[F[Throwable, _], Option[ClosableFile]] = {
      Lifecycle.make {
        download(key).flatMap {
          F.traverse(_) {
            response =>
              F.syncThrowable {
                val file = path.fold {
                  key
                    .split("/").lastOption
                    .map(full => full.splitAt(full.lastIndexOf(".")))
                    .fold(ClosableFile.createTempFile()) { case (p, s) => ClosableFile.createTempFile(p, s) }
                }(ClosableFile.createFile)
                Files.write(file.path, response.content, StandardOpenOption.TRUNCATE_EXISTING)
                file
              }
          }
        }
      } {
        case Some(value) => F.sync(value.close())
        case None        => F.unit
      }
    }
    override def doesObjectExists(key: String): F[Throwable, Boolean] = F.sync(synchronized {
      content.contains(key)
    })
    override def delete(key: String): F[Throwable, Unit] = F.sync(synchronized {
      content.remove(key).discard()
    })
    override def deleteBatch(keys: List[String]): F[Throwable, Unit] = F.sync(synchronized {
      keys.foreach(content.remove)
    })
    override def markObjectForDeletion(key: String): F[Throwable, Unit] = {
      F.unit
    }
    override def listObjects(prefix: String): F[Throwable, List[String]] = F.sync(synchronized {
      content.keySet.filter(_.startsWith(prefix)).toList
    })
    override def listObjectsMeta(prefix: String): F[Throwable, List[S3ObjectMeta]] = F.sync(synchronized {
      content
        .filter(_._1.startsWith(prefix)).toList
        .map {
          case (k, (r, at)) =>
            val size = r.content match {
              case S3UploadContent.Bytes(bytes, _)         => bytes.length.toLong
              case S3UploadContent.File(file, _)           => file.length
              case S3UploadContent.String(string, _)       => string.getBytes.length.toLong
              case S3UploadContent.Stream(_, length, _, _) => length
              case json: S3UploadContent.Json[?]           => json.stringBody.getBytes.length.toLong
            }
            models.S3ObjectMeta(
              bucketName = bucketName,
              key        = k,
              eTag       = "test",
              size       = size,
              modifiedAt = at.toInstant,
            )
        }
    })
    override def listFolders(prefix: String, delimiter: Char): F[Throwable, List[String]] = F.sync(synchronized {
      content
        .filter(_._1.startsWith(prefix))
        .map(_._1.takeWhile(_ != delimiter))
        .toList.distinct
    })
    override def streamObjects(prefix: String): Stream[F[Throwable, _], String] = {
      fs2.Stream.evalSeq(listObjects(prefix))
    }
    override def streamObjectsMeta(prefix: String): Stream[F[Throwable, _], S3ObjectMeta] = {
      fs2.Stream.evalSeq(listObjectsMeta(prefix))
    }
    override def streamFolders(prefix: String, delimiter: Char): Stream[F[Throwable, _], String] = {
      fs2.Stream.evalSeq(listFolders(prefix, delimiter))
    }
    override def copyObject(sourceKey: String, targetBucket: String, targetKey: String): F[Throwable, Unit] = {
      F.fail(new RuntimeException("Dummy buckets can not interact with each other."))
    }

    override def copyObject(sourceKey: String, targetKey: String): F[Throwable, Unit] = F.syncThrowable(synchronized {
      val (c, _)    = content.getOrElseUpdate(sourceKey, throw new RuntimeException("Key not found."))
      val newFile   = c.file.copy(fileName = targetKey)
      val newRecord = c.copy(file = newFile)
      content.addOne(targetKey -> (newRecord -> Clock1.Standard.nowZoned())).discard()
    })

    override def getObjectPresigned(
      location: String,
      responseContentType: Option[String],
      responseContentDisposition: Option[String],
      responseCacheControl: Option[String],
    ): F[Throwable, URL] = F.sync {
      val b64 = Base64.getEncoder.encodeToString(location.getBytes)
      new URI(s"http://localhost/$b64").toURL
    }

    override def putObjectPresigned(
      location: String,
      contentType: Option[String],
      sha256B64: Option[String],
      tags: List[Tag],
    ): F[Throwable, PresignedPut] = F.sync {
      val b64 = Base64.getEncoder.encodeToString(location.getBytes)
      PresignedPut(new URI(s"http://localhost/$b64").toURL, Map.empty)
    }

    override def tagObject[T: S3Tag](location: String, tag: T): F[Throwable, Unit]  = F.unit
    override def getObjectTags(location: String): F[Throwable, Map[String, String]] = F.pure(Map.empty)

    override def getArn: F[Throwable, String]                                                      = F.pure(s"arn:aws:s3:*:*:$bucketName")
    override def getBucketTags: F[Throwable, Map[String, String]]                                  = F.pure(Map.empty)
    override def setBucketTags(tags: Seq[model.Tag]): F[Throwable, Unit]                           = F.unit
    override def configureSQSNotifications(sqsArn: String, events: Set[Event]): F[Throwable, Unit] = F.unit
    override def healthProbe: F[Throwable, Unit]                                                   = F.unit
    override def getBucketCors: F[Throwable, Set[CorsRule]]                                        = F.pure(Set.empty)
    override def setBucketCors(rules: Set[CorsRule]): F[Throwable, Unit]                           = F.unit
  }

  final class Impl[F[+_, +_]: Async2: AsyncThrowable, BucketId <: S3BucketId](
    bucketId: BucketId,
    component: S3Component[F],
  )(implicit
    log: LogIO2[F]
  ) extends S3Bucket[F, BucketId] {
    override val bucket: BucketId         = bucketId
    override val bucketName: String       = bucketId.bucketName
    override val bucketIdentifier: String = s"s3:${component.componentIdentifier}:${bucketId.bucketName}"

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
          _.getObjectTagging(GetObjectTaggingRequest.builder.bucket(bucketName).key(location).build)
        }.map(old => old.tagSet.asScala.map(t => t.key -> t.value).toMap)
        .logThrowable("GetObjectTagging", bucketName)
    }

    private[this] def _tagObject(location: String, tag: Tag): F[Throwable, Unit] = {
      component
        .rawRequestF("tag-object") {
          client =>
            for {
              old    <- F.fromFutureJava(client.getObjectTagging(GetObjectTaggingRequest.builder.bucket(bucketName).key(location).build))
              newTags = Tagging.builder.tagSet((tag :: old.tagSet.asScala.toList).asJava).build
              _      <- F.fromFutureJava(client.putObjectTagging(PutObjectTaggingRequest.builder.bucket(bucketName).key(location).tagging(newTags).build))
            } yield ()
        }.logThrowable("SetObjectTagging", bucketName)
    }

    override def getObjectPresigned(
      location: String,
      responseContentType: Option[String],
      responseContentDisposition: Option[String],
      responseCacheControl: Option[String],
    ): F[Throwable, URL] = {
      component
        .presign("generate-presigned-get") {
          presigner =>
            val getRequest = GetObjectRequest.builder
              .bucket(bucketName).key(location)
              .pipe(builder => responseContentType.fold(builder)(builder.responseContentType))
              .pipe(builder => responseContentDisposition.fold(builder)(builder.responseContentDisposition))
              .pipe(builder => responseCacheControl.fold(builder)(builder.responseCacheControl))
              .build()
            val presignedGet = GetObjectPresignRequest.builder.signatureDuration(java.time.Duration.ofMinutes(15)).getObjectRequest(getRequest).build
            presigner.presignGetObject(presignedGet).url
        }.logThrowable("GetObjectPresign", bucketName)
    }

    override def putObjectPresigned(
      location: String,
      contentType: Option[String],
      sha256B64: Option[String],
      tags: List[Tag],
    ): F[Throwable, PresignedPut] = {
      component
        .presign("generate-presigned-put") {
          presigner =>
            val tagging    = if (tags.nonEmpty) Some(Tagging.builder.tagSet(tags.toSet.asJava).build) else None
            val putRequest = PutObjectRequest.builder
              .bucket(bucketName).key(location)
              .pipe(b => tagging.fold(b)(b.tagging))
              .pipe(b => sha256B64.fold(b)(b.checksumSHA256))
              .pipe(b => contentType.fold(b)(b.contentType))
              .build
            val presignRequest = PutObjectPresignRequest.builder
              .signatureDuration(java.time.Duration.ofMinutes(15))
              .putObjectRequest(putRequest)
              .build
            val presigned = presigner.presignPutObject(presignRequest)
            PresignedPut(presigned.url, presigned.signedHeaders.asScala.view.mapValues(_.asScala.toList).toMap)
        }.logThrowable("PutObjectPresign", bucketName)
    }

    override def getBucketTags: F[Throwable, Map[String, String]] = {
      component
        .rawRequestF("get-tags") {
          client =>
            F.fromFutureJava(client.getBucketTagging(GetBucketTaggingRequest.builder.bucket(bucketName).build))
              .map(_.tagSet.asScala.map(t => t.key -> t.value).toMap)
              .catchAll(_ => F.pure(Map.empty[String, String]))
        }
    }

    override def setBucketTags(tags: Seq[Tag]): F[Throwable, Unit] = {
      component
        .rawRequest("set-tags") {
          client =>
            val tagging = Tagging.builder.tagSet(tags.asJava).build
            client.putBucketTagging(PutBucketTaggingRequest.builder.bucket(bucketName).tagging(tagging).build)
        }.logThrowable("SetBucketTaggingConfiguration", bucketName).void
    }

    override def healthProbe: F[Throwable, Unit] = {
      component
        .rawRequestF("health-probe") {
          s3 =>
            val filePath    = "health-probe.txt"
            val fileContent = "test"
            val upload      = S3Record(S3File("health-probe.txt", S3FileFormat.Unknown), S3UploadContent.String("test"))
            for {
              _        <- uploadImpl(upload)(s3)
              download <- downloadImpl(filePath)(s3).fromOption(new RuntimeException("Failed to download file."))
              response  = new String(download.content, StandardCharsets.UTF_8)
              _        <- F.when(response != fileContent)(F.fail(new RuntimeException("Failed to validate object content.")))
            } yield ()
        }
    }

    override def getArn: F[Throwable, String] = {
      F.pure(s"arn:aws:s3:*:*:$bucketName")
    }

    override def configureSQSNotifications(sqsArn: String, events: Set[Event]): F[Throwable, Unit] = {
      component.rawRequestF("configure-sqs-notifications") {
        client =>
          for {
            configs <- F.fromFutureJava {
              client.getBucketNotificationConfiguration(GetBucketNotificationConfigurationRequest.builder().bucket(bucketName).build())
            }.logThrowable("GetBucketNotificationConfiguration", bucketName)
            oldConfigs = configs.queueConfigurations().asScala.toList
            _         <-
              if (oldConfigs.exists(c => c.queueArn() == sqsArn && c.events().asScala.toSet.diff(events).isEmpty)) {
                log.info(s"$bucketName already configured to notify $sqsArn.")
              } else {
                val queueConf        = QueueConfiguration.builder.queueArn(sqsArn).events(events.asJavaCollection).id("sqsQueueConfig").build()
                val notificationConf = NotificationConfiguration.builder().queueConfigurations(queueConf).build()
                val newConf          = PutBucketNotificationConfigurationRequest.builder().bucket(bucketName).notificationConfiguration(notificationConf).build()
                log.info(s"Updating $bucketName notification config to use $sqsArn.") *>
                F.fromFutureJava(client.putBucketNotificationConfiguration(newConf)).logThrowable("SetBucketNotificationConfiguration", bucketName)
              }
          } yield ()
      }
    }

    override def upload(record: S3Record[S3UploadContent]): F[Throwable, Unit] = {
      objectOp("PutObject", record.file.fileName, "uploading")(uploadImpl(record)).void
    }

    override def download(key: String): F[Throwable, Option[S3DownloadResponse]] = {
      objectOp("GetObject", key, "downloading")(downloadImpl(key))
    }

    override def meta(key: String): F[Throwable, Option[S3ObjectMeta]] = {
      streamObjectsImpl(key, "get-object-meta", maxKeys = 1)((_, objs) => objs.map(toMeta(bucketName))).take(1).compile.toList.map(_.headOption)
    }

    override def downloadToFile(key: String, path: Option[Path]): Lifecycle[F[Throwable, _], Option[ClosableFile]] = {
      Lifecycle.make {
        objectOp("GetObject", key, "downloading")(downloadToFileImpl(key, path))
      } {
        case Some(value) => F.sync(value.close())
        case None        => F.unit
      }
    }

    override def doesObjectExists(key: String): F[Throwable, Boolean] = {
      component
        .rawRequestF("object-exist") {
          client =>
            F.fromFutureJava {
              client.getObjectTagging(GetObjectTaggingRequest.builder.bucket(bucketName).key(key).build)
            }.as(true).catchAll {
              case _: NoSuchKeyException                                                                               => F.pure(false)
              case e: Throwable if e.getMessage.contains("The specified method is not allowed against this resource.") => F.pure(false)
              case e: Throwable                                                                                        => F.fail(e)
            }
        }.logThrowable("GetObjectMetadata", bucketName)
    }

    override def delete(key: String): F[Throwable, Unit] = {
      objectOp("DeleteObject", key, "deleting")(deleteImpl(key))
    }

    override def deleteBatch(keys: List[String]): F[Throwable, Unit] = {
      objectOp("DeleteObjects", "N/A", "deleting-batch")(deleteBatchImpl(keys))
    }

    override def listObjects(prefix: String): F[Throwable, List[String]] = {
      streamObjects(prefix).compile.toList
    }

    override def listObjectsMeta(prefix: String): F[Throwable, List[S3ObjectMeta]] = {
      streamObjectsMeta(prefix).compile.toList
    }

    override def listFolders(prefix: String, delimiter: Char = '/'): F[Throwable, List[String]] = {
      streamFolders(prefix, delimiter).compile.toList
    }

    override def copyObject(sourceKey: String, targetBucket: String, targetKey: String): F[Throwable, Unit] = {
      objectOp("CopyingObject", bucketName, "copy-object")(copyObjectImpl(bucketName, sourceKey, targetBucket, targetKey))
    }

    override def copyObject(sourceKey: String, targetKey: String): F[Throwable, Unit] = {
      copyObject(sourceKey, bucketName, targetKey)
    }

    override def streamObjects(prefix: String): Stream[F[Throwable, _], String] = {
      streamObjectsImpl(prefix, "list-bucket-prefix-ops")((_, objs) => objs.map(_.key))
    }

    override def streamObjectsMeta(prefix: String): Stream[F[Throwable, _], S3ObjectMeta] = {
      streamObjectsImpl(prefix, "list-bucket-prefix-ops")((_, objs) => objs.map(toMeta(bucketName)))
    }

    override def streamFolders(prefix: String, delimiter: Char = '/'): Stream[F[Throwable, _], String] = {
      streamObjectsImpl(
        prefix,
        "list-bucket-prefix-ops",
        _.delimiter(delimiter.toString),
      )((rsp, _) => rsp.commonPrefixes().iterator.asScala.map(_.prefix()).toList)
    }

    override def getBucketCors: F[Throwable, Set[CorsRule]] = {
      component
        .rawRequestF("get-cors") {
          client =>
            F.fromFutureJava(client.getBucketCors(GetBucketCorsRequest.builder.bucket(bucketName).build)).map {
              _.corsRules().asScala.map {
                rule =>
                  CorsRule(
                    methods       = rule.allowedMethods().asScala.toSet,
                    headers       = rule.allowedHeaders().asScala.toSet,
                    origins       = rule.allowedOrigins().asScala.toSet,
                    exposeHeaders = rule.exposeHeaders().asScala.toSet,
                    maxAgeSeconds = rule.maxAgeSeconds(),
                  )
              }.toSet
            }.catchSome {
              case err if err.getMessage.contains("The CORS configuration does not exist") =>
                log.info(s"CORS configuration of $bucketName does not exists.").as(Set.empty[CorsRule])
            }
        }.logThrowable("GetBucketCORS", bucketName)
    }

    override def setBucketCors(rules: Set[CorsRule]): F[Throwable, Unit] = {
      component
        .rawRequest("set-cors") {
          client =>
            val config = CORSConfiguration.builder().corsRules(rules.toList.map(_.toCORSRule).asJavaCollection).build()
            val req    = PutBucketCorsRequest.builder().bucket(bucketName).corsConfiguration(config).build()
            client.putBucketCors(req)
        }.logThrowable("PutBucketCORS", bucketName).void
    }

    private[this] def objectOp[R](
      opName: String,
      objKey: String,
      metric: String,
    )(cont: S3AsyncClient => F[Throwable, R]
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

    private[this] def uploadImpl(record: S3Record[S3UploadContent])(client: S3AsyncClient): F[Throwable, PutObjectResponse] = {
      F.fromFutureJava {
        val fileName        = record.file.fileName
        val (request, body) = record.content.toRequest(bucketName, fileName)(
          _.contentType(record.file.format.contentType).pipe(b => record.cacheControl.fold(b)(b.cacheControl))
        )

        client.putObject(request, body)
      }
    }

    private[this] def downloadImpl(key: String)(client: S3AsyncClient): F[Throwable, Option[S3DownloadResponse]] = {
      F.fromFutureJava {
        val request     = GetObjectRequest.builder.bucket(bucketName).key(key).build()
        val transformer = AsyncResponseTransformer.toBytes[GetObjectResponse]
        client.getObject(request, transformer)
      }.uninterruptible.map {
        response =>
          Some(S3DownloadResponse(response.asByteArray(), response.response.contentType, response.response.lastModified))
      }.catchSome {
        case _: NoSuchKeyException                                                                              => F.pure(None)
        case e: Throwable if e.getMessage.contains("The specified key does not exist")                          => F.pure(None)
        case e: Throwable if e.getMessage.contains("The specified method is not allowed against this resource") => F.pure(None)
      }
    }

    private[this] def fileTransformerConfiguration: FileTransformerConfiguration = {
      FileTransformerConfiguration
        .builder()
        .executorService(component.executorService)
        .fileWriteOption(FileWriteOption.CREATE_OR_REPLACE_EXISTING)
        .failureBehavior(FailureBehavior.DELETE)
        .build()
    }

    private[this] def downloadToFileImpl(key: String, path: Option[Path])(client: S3AsyncClient): F[Throwable, Option[ClosableFile]] = {
      for {
        file <- F.syncThrowable {
          path.fold {
            key
              .split("/").lastOption
              .map(full => full.splitAt(full.lastIndexOf(".")))
              .fold(ClosableFile.createTempFile("s3-tmp")) { case (p, s) => ClosableFile.createTempFile(p, s) }
          }(ClosableFile.createFile)
        }
        request     = GetObjectRequest.builder.bucket(bucketName).key(key).build()
        transformer = AsyncResponseTransformer.toFile[GetObjectResponse](file.file, fileTransformerConfiguration)
        res        <- F
          .fromFutureJava(client.getObject(request, transformer))
          .uninterruptible
          .guaranteeCase {
            case Exit.Success(_)    => F.unit
            case _: Exit.Failure[?] => F.sync(file.close())
          }.map(_ => Some(file)).catchSome {
            case _: NoSuchKeyException                                                                              => F.pure(None)
            case e: Throwable if e.getMessage.contains("The specified key does not exist")                          => F.pure(None)
            case e: Throwable if e.getMessage.contains("The specified method is not allowed against this resource") => F.pure(None)
          }
      } yield res
    }

    private[this] def deleteImpl(key: String)(client: S3AsyncClient): F[Throwable, Unit] = {
      val request = DeleteObjectRequest.builder().bucket(bucketName).key(key).build()
      F.fromFutureJava(client.deleteObject(request)).void
    }

    private[this] def deleteBatchImpl(keys: List[String])(client: S3AsyncClient): F[Throwable, Unit] = {
      F.traverse_(keys.grouped(999).toList) {
        keys =>
          val objects = keys.map(k => ObjectIdentifier.builder().key(k).build())
          val delete  = Delete.builder().objects(objects.asJavaCollection).quiet(true).build()
          val request = DeleteObjectsRequest.builder().bucket(bucketName).delete(delete).build()
          F.fromFutureJava(client.deleteObjects(request)).void
      }
    }

    private[this] def copyObjectImpl(sourceBucket: String, sourceKey: String, targetBucket: String, targetKey: String)(client: S3AsyncClient): F[Throwable, Unit] = {
      val request = CopyObjectRequest
        .builder()
        .sourceBucket(sourceBucket)
        .sourceKey(sourceKey)
        .destinationBucket(targetBucket)
        .destinationKey(targetKey)
        .build()
      F.fromFutureJava(client.copyObject(request)).void
    }

    // unused for now
    @unused
    private[this] def streamObjectsV2Impl[A](
      prefix: String,
      metric: String,
      modifyRequest: ListObjectsV2Request.Builder => ListObjectsV2Request.Builder,
      maxKeys: Int,
    )(f: (ListObjectsV2Response, List[S3Object]) => List[A]
    )(implicit
      saveCounter: MacroMetricS3Meter[metric.type],
      saveTimer: MacroMetricS3Timer[metric.type],
    ): Stream[F[Throwable, _], A] = {
      def buildRequest(continuationToken: Option[String]): ListObjectsV2Request = {
        ListObjectsV2Request
          .builder().bucket(bucketName).prefix(prefix)
          .maxKeys(maxKeys).pipe(modifyRequest)
          .pipe(b => continuationToken.fold(b)(b.continuationToken))
          .build()
      }
      Stream
        .unfoldLoopEval[F[Throwable, _], Option[String], Chunk[A]](Option.empty[String]) {
          maybeToken =>
            objectOp("ListObjectsV2", prefix, metric) {
              client =>
                (for {
                  request  <- F.pure(buildRequest(maybeToken))
                  response <- F.fromFutureJava(client.listObjectsV2(request))
                } yield {
                  val s3Objects = response.contents().asScala.toList
                  val listRes   = f(response, s3Objects)
                  Chunk.from(listRes) -> Option.when(response.isTruncated)(Some(response.nextContinuationToken()))
                }).logThrowable("ListObjectsV2", bucketName)
            }
        }.unchunks
    }

    @SuppressWarnings(Array("UnsafeTraversableMethods"))
    private[this] def streamObjectsImpl[A](
      prefix: String,
      metric: String,
      modifyRequest: ListObjectsRequest.Builder => ListObjectsRequest.Builder = identity,
      maxKeys: Int                                                            = 1000,
    )(f: (ListObjectsResponse, List[S3Object]) => List[A]
    )(implicit
      saveCounter: MacroMetricS3Meter[metric.type],
      saveTimer: MacroMetricS3Timer[metric.type],
    ) = {
      def buildRequest(marker: Option[String]): ListObjectsRequest = {
        ListObjectsRequest
          .builder()
          .bucket(bucketName).prefix(prefix)
          .maxKeys(maxKeys).pipe(modifyRequest)
          .pipe(b => marker.fold(b)(b.marker))
          .build()
      }
      Stream
        .unfoldLoopEval[F[Throwable, _], Option[String], Chunk[A]](Option.empty[String]) {
          maybeMarker =>
            objectOp("ListObjects", prefix, metric) {
              client =>
                (for {
                  request  <- F.pure(buildRequest(maybeMarker))
                  response <- F.fromFutureJava(client.listObjects(request))
                } yield {
                  val s3Objects = response.contents().asScala.toList
                  val listRes   = f(response, s3Objects)
                  val next      = if (response.isTruncated) {
                    val maybeMarker = Option(response.nextMarker()).orElse(s3Objects.lastOption.map(_.key()))
                    Some(maybeMarker)
                  } else {
                    None
                  }
                  Chunk.from(listRes) -> next
                }).logThrowable("ListObjects", bucketName)
            }
        }.unchunks
    }

    private[this] def toMeta(bucket: String)(summary: S3Object) = {
      S3ObjectMeta(
        bucketName = bucket,
        key        = summary.key,
        eTag       = summary.eTag,
        size       = summary.size,
        modifiedAt = summary.lastModified,
      )
    }
  }

  private[s3] implicit final class ThrowableS3Ops[F[+_, +_], A](private val f: F[Throwable, A]) extends AnyVal {
    def logThrowable(operation: String, bucketName: String)(implicit F: Error2[F], log: LogIO2[F]): F[Throwable, A] = {
      f.tapError {
        case _: NoSuchKeyException => F.unit
        case failure               =>
          log.error(s"S3: Got error during executing $operation for $bucketName. ${failure.getMessage -> "Failure"}. ${failure.fillInStackTrace() -> "Trace"}")
      }
    }
  }
}
