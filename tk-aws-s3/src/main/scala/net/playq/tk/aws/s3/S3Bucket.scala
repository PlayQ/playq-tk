package net.playq.tk.aws.s3

import distage.{Tag, TagKK}
import fs2.Stream
import izumi.distage.constructors.ClassConstructor
import izumi.distage.model.definition.{Lifecycle, ModuleDef}
import izumi.functional.bio.{F, IO2}
import izumi.fundamentals.platform.language.Quirks._
import izumi.fundamentals.platform.time.IzTime
import net.playq.tk.aws.s3.models._
import net.playq.tk.quantified.SyncThrowable
import net.playq.tk.util.ManagedFile
import software.amazon.awssdk.services.s3.model
import software.amazon.awssdk.services.s3.model.Event

import java.net.URL
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.ZonedDateTime
import java.util.Base64
import scala.collection.concurrent.TrieMap

trait S3Bucket[F[_, _], BucketId] {
  val name: String

  def upload(record: S3Record[S3UploadContent]): F[Throwable, Unit]
  def download(key: String): F[Throwable, S3DownloadResponse]

  def downloadToFile(key: String, path: Option[Path] = None): Lifecycle[F[Throwable, ?], ManagedFile]

  def doesObjectExists(key: String): F[Throwable, Boolean]
  def delete(key: String): F[Throwable, Unit]
  def markObjectForDeletion(key: String): F[Throwable, Unit]

  def listObjects(prefix: String): F[Throwable, List[String]]
  def listObjectsMeta(prefix: String): F[Throwable, List[S3ObjectMeta]]

  def streamObjects(prefix: String): Stream[F[Throwable, ?], String]
  def streamObjectsMeta(prefix: String): Stream[F[Throwable, ?], S3ObjectMeta]

  def copyObject(sourceKey: String, targetBucket: String, targetKey: String): F[Throwable, Unit]
  def copyObject(sourceKey: String, targetKey: String): F[Throwable, Unit]

  def getObjectPresigned(location: String, responseContentType: Option[String], responseContentDisposition: Option[String]): F[Throwable, URL]
  def putObjectPresigned(location: String, contentType: Option[String], tags: Map[String, String]): F[Throwable, URL]

  def tagObject[T: S3Tag](location: String, tag: T): F[Throwable, Unit]
  def getObjectTags(location: String): F[Throwable, Map[String, String]]

  def getArn: F[Throwable, String]
  def getBucketTags: F[Throwable, Map[String, String]]
  def setBucketTags(tags: model.Tag*): F[Throwable, Unit]
  def configureSQSNotifications(sqsArn: String, events: Set[Event]): F[Throwable, Unit]
  def healthProbe: F[Throwable, Unit]

  override final def toString: String = name
}

object S3Bucket {

  def bucketModule[F[+_, +_]: TagKK, BucketId <: S3BucketId: Tag: ClassConstructor]: ModuleDef = new ModuleDef {
    make[BucketId]
    make[S3Bucket[F, BucketId]].fromResource((_: S3BucketFactory[F]).mkClient(_: BucketId))
    many[S3BucketId]
      .weak[BucketId]
  }

  def bucketCachedModule[F[+_, +_]: TagKK, BucketId <: S3BucketId: Tag: ClassConstructor]: ModuleDef = new ModuleDef {
    include(bucketModule[F, BucketId])
    make[S3BucketCached[F, BucketId]].fromResource[S3BucketCached.Default[F, BucketId]]
  }

  final class Dummy[F[+_, +_]: IO2: SyncThrowable, BucketId <: S3BucketId](bucketId: BucketId) extends S3Bucket[F, BucketId] {
    override val name: String = bucketId.bucketName

    private[this] val content = TrieMap.empty[String, (S3Record[S3UploadContent], ZonedDateTime)]

    override def upload(record: S3Record[S3UploadContent]): F[Throwable, Unit] = F.sync(synchronized {
      content.addOne(record.file.fileName -> (record -> IzTime.utcNow)).discard()
    })

    override def download(key: String): F[Throwable, S3DownloadResponse] = F.syncThrowable(synchronized {
      val (c, _) = content.getOrElseUpdate(key, throw new RuntimeException("Key not found."))
      c.content match {
        case S3UploadContent.Bytes(bytes, _)      => S3DownloadResponse(bytes, c.file.format.contentType)
        case S3UploadContent.File(file, _)        => S3DownloadResponse(Files.readAllBytes(file.toPath), c.file.format.contentType)
        case S3UploadContent.String(string, _)    => S3DownloadResponse(string.getBytes(), c.file.format.contentType)
        case S3UploadContent.Stream(stream, _, _) => S3DownloadResponse(stream.readAllBytes(), c.file.format.contentType)
      }
    })

    override def downloadToFile(key: String, path: Option[Path]): Lifecycle[F[Throwable, ?], ManagedFile] = {
      Lifecycle.fromAutoCloseable {
        download(key).flatMap {
          response =>
            F.syncThrowable {
              val file = path.fold {
                key
                  .split("/").lastOption
                  .map(full => full.splitAt(full.lastIndexOf(".")))
                  .fold(ManagedFile.createFile()) { case (p, s) => ManagedFile.createFile(p, s) }
              }(ManagedFile.managedExternal)
              Files.write(file.path, response.content, StandardOpenOption.TRUNCATE_EXISTING)
              file
            }
        }
      }
    }

    override def doesObjectExists(key: String): F[Throwable, Boolean] = F.sync(synchronized {
      content.contains(key)
    })
    override def delete(key: String): F[Throwable, Unit] = F.sync(synchronized {
      content.remove(key).discard()
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
              case S3UploadContent.Bytes(bytes, _)      => bytes.length.toLong
              case S3UploadContent.File(file, _)        => file.length
              case S3UploadContent.String(string, _)    => string.getBytes.length.toLong
              case S3UploadContent.Stream(_, length, _) => length
            }
            models.S3ObjectMeta(name, k, "test", size, at.toEpochSecond)
        }
    })
    override def streamObjects(prefix: String): Stream[F[Throwable, ?], String] = {
      fs2.Stream.eval(listObjects(prefix)).flatMap(l => fs2.Stream.fromIterator(l.iterator))
    }
    override def streamObjectsMeta(prefix: String): Stream[F[Throwable, ?], S3ObjectMeta] = {
      fs2.Stream.eval(listObjectsMeta(prefix)).flatMap(l => fs2.Stream.fromIterator(l.iterator))
    }
    override def copyObject(sourceKey: String, targetBucket: String, targetKey: String): F[Throwable, Unit] = {
      F.fail(new RuntimeException("Dummy buckets can not interact with each other."))
    }

    override def copyObject(sourceKey: String, targetKey: String): F[Throwable, Unit] = F.syncThrowable(synchronized {
      val (c, _)    = content.getOrElseUpdate(sourceKey, throw new RuntimeException("Key not found."))
      val newFile   = c.file.copy(fileName = targetKey)
      val newRecord = c.copy(file = newFile)
      content.addOne(targetKey -> (newRecord -> IzTime.utcNow)).discard()
    })

    override def getObjectPresigned(location: String, responseContentType: Option[String], responseContentDisposition: Option[String]): F[Throwable, URL] = F.sync {
      val b64 = Base64.getEncoder.encodeToString(location.getBytes)
      new URL(s"http://localhost/$b64")
    }

    override def putObjectPresigned(location: String, contentType: Option[String], tags: Map[String, String]): F[Throwable, URL] = F.sync {
      val b64 = Base64.getEncoder.encodeToString(location.getBytes)
      new URL(s"http://localhost/$b64")
    }

    override def tagObject[T: S3Tag](location: String, tag: T): F[Throwable, Unit]  = F.unit
    override def getObjectTags(location: String): F[Throwable, Map[String, String]] = F.pure(Map.empty)

    override def getArn: F[Throwable, String]                                                      = F.pure(s"arn:aws:s3:*:*:$name")
    override def getBucketTags: F[Throwable, Map[String, String]]                                  = F.pure(Map.empty)
    override def setBucketTags(tags: model.Tag*): F[Throwable, Unit]                               = F.unit
    override def configureSQSNotifications(sqsArn: String, events: Set[Event]): F[Throwable, Unit] = F.unit
    override def healthProbe: F[Throwable, Unit]                                                   = F.unit
  }

}
