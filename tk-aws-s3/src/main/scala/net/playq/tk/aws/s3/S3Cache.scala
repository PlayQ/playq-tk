package net.playq.tk.aws.s3

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, Clock2, F, Fork2, Primitives2, Ref2, Temporal2}
import izumi.fundamentals.platform.files.FileLockMutex
import logstage.LogIO2
import net.playq.tk.util.ClosableFile
import org.apache.commons.io.FileUtils

import java.io.File
import java.time.ZonedDateTime
import scala.concurrent.duration.*

trait S3Cache[F[_, _]] {
  def get(bucket: S3Bucket[F, ?], key: String): Lifecycle[F[Throwable, _], ClosableFile]
  private[s3] def acquire(bucket: S3Bucket[F, ?], key: String): F[Throwable, ClosableFile]
  private[s3] def release(bucket: S3Bucket[F, ?], key: String): F[Nothing, Unit]
  def evict(bucket: S3Bucket[F, ?], key: String): F[Nothing, Unit]
  def cleanUnused(): F[Nothing, Unit]
  def cleanAll(): F[Nothing, Unit]
}

object S3Cache {
  final case class S3CacheConfig(
    deleteAfter: FiniteDuration
  )

  final case class CachedFile(
    managedFile: ClosableFile,
    createdAt: ZonedDateTime,
    cleanupAfter: ZonedDateTime,
    inUse: Int = 1,
  ) {
    def acquire: CachedFile                  = copy(inUse = inUse + 1)
    def release: CachedFile                  = copy(inUse = math.max(inUse - 1, 0))
    def expired(now: ZonedDateTime): Boolean = {
      unused && cleanupAfter.isBefore(now)
    }
    def unused: Boolean = {
      inUse <= 0
    }
  }

  final class Resource[F[+_, +_]: Async2: Fork2: Primitives2: Temporal2](
    clock: Clock2[F],
    config: S3CacheConfig,
  )(implicit val logger: LogIO2[F]
  ) extends Lifecycle.Basic[F[Throwable, _], S3Cache[F]] {
    override def acquire: F[Throwable, S3Cache[F]] = {
      for {
        keyToFile  <- F.mkRef(Map.empty[String, CachedFile])
        storageDir <- F.sync(FileUtils.getTempDirectory)
      } yield new Default(keyToFile, storageDir, config, clock)
    }
    override def release(resource: S3Cache[F]): F[Throwable, Unit] = {
      resource.cleanAll()
    }
  }

  final class Default[F[+_, +_]: Async2: Fork2: Temporal2](
    keyToFile: Ref2[F, Map[String, CachedFile]],
    storageDir: File,
    config: S3CacheConfig,
    clock: Clock2[F],
  )(implicit val logger: LogIO2[F]
  ) extends S3Cache[F] {
    private[this] val totalSpace = storageDir.getTotalSpace

    override def get(bucket: S3Bucket[F, ?], key: String): Lifecycle[F[Throwable, _], ClosableFile] = {
      Lifecycle.make(acquire(bucket, key))(_ => release(bucket, key))
    }

    /**
      * Operations on [[keyToFile]] is atomic, but anyway, we can not execute effect under [[keyToFile.modify]].
      * That's why we need to add [[FileLockMutex]] on file to avoid races.
      *
      * It's still safe not to use mutex at all,
      * but in case of parallel file creation we might create 2 files with the same content, and save only one [[CachedFile]] instance.
      * In case of duplicated file creation we can lose file from cached scope. We may use Set instead of map for state,
      * but it will be slower in common cases. Also, time consumed during mutex awaiting will be the same as for file downloading so it should be pretty fast.
      */
    private[s3] def acquire(bucket: S3Bucket[F, ?], key: String): F[Throwable, ClosableFile] = {
      for {
        _       <- cleanupCache().fork
        fullKey  = bucket.bucketIdentifier + key
        hashcode = fullKey.hashCode.toString
        _       <- logger.debug(s"S3Cache: Trying to acquire: $fullKey")
        res     <- FileLockMutex.withLocalMutex[F[Throwable, _], ClosableFile](
          s"s3cache-lock-$hashcode",
          100.millis,
          1000,
          attemptLog            = (num, maxAttempts) => logger.debug(s"Attempt $num out of $maxAttempts to acquire file lock for $hashcode."),
          failLog               = attempts => logger.warn(s"Cannot acquire file lock for $hashcode after $attempts."),
          lockAlreadyExistedLog = logger.debug(s"File lock already existed for $hashcode"),
        ) {
          keyToFile.get.map(_.get(fullKey)).flatMap {
            case None =>
              for {
                _            <- logger.debug(s"S3Cache: downloaded and acquired file: $fullKey")
                file         <- bucket.downloadToFile(key).unsafeGet().fromOption(new RuntimeException(s"S3 file $key does not exists."))
                now          <- clock.nowZoned()
                cacheInternal = CachedFile(file, now, now.plusSeconds(config.deleteAfter.toSeconds))
                _            <- keyToFile.update(_ ++ Map(fullKey -> cacheInternal))
              } yield file
            case Some(cacheInternal) =>
              for {
                _ <- logger.debug(s"S3Cache: acquired file: $fullKey")
                _ <- keyToFile.update(_ ++ Map(fullKey -> cacheInternal.acquire))
              } yield cacheInternal.managedFile
          }
        }
      } yield res
    }

    private[s3] def release(bucket: S3Bucket[F, ?], key: String): F[Nothing, Unit] = {
      val fullKey = bucket.bucketIdentifier + key
      for {
        _ <- logger.debug(s"S3Cache: file released: $fullKey")
        _ <- keyToFile.update_(_.updatedWith(fullKey)(_.map(_.release)))
      } yield ()
    }

    override def evict(bucket: S3Bucket[F, ?], key: String): F[Nothing, Unit] = {
      val fullKey = bucket.bucketIdentifier + key
      for {
        cached <- keyToFile.modify {
          state =>
            state.get(fullKey) match {
              case Some(cached) if cached.unused => Some(cached) -> state.removed(fullKey)
              case _                             => None         -> state
            }
        }
        _ <- cached match {
          case Some(value) =>
            F.sync(value.managedFile.close()) *> logger.debug(s"S3Cache: file evicted: $fullKey")
          case None =>
            logger.warn(s"S3Cache: file can not be evicted (still in use or does not persists in cache): $fullKey")
        }
      } yield ()
    }

    override def cleanUnused(): F[Nothing, Unit] = {
      for {
        unused <- keyToFile.modify(_.partition(_._2.unused))
        _      <- F.sync(unused.map(_._2.managedFile.close()))
        _      <- logger.debug(s"S3Cache: evicted all unused files.")
      } yield ()
    }

    override def cleanAll(): F[Nothing, Unit] = {
      keyToFile.update_ {
        old =>
          old.foreach(_._2.managedFile.close())
          Map.empty
      }
    }

    /** We will cleanup cache if there left 30% of available space (available space is a space that was available on start)
      * Firstly we will delete all expired files, then if space still not enough we will delete all unused files.
      * Also, we don't need to add synchronization over file since local state modification is atomic and even each files location is unique.
      */
    private[this] def cleanupCache(): F[Nothing, Unit] = {
      val spaceBeforeClean = storageDir.getFreeSpace
      F.when((spaceBeforeClean.toDouble / totalSpace.toDouble) < 0.3) {
        for {
          now     <- clock.nowZoned()
          expired <- keyToFile.modify(_.partition(_._2.expired(now)))
          _       <- F.sync(expired.map(_._2.managedFile.close()))
          _       <- F.when((storageDir.getFreeSpace.toDouble / totalSpace.toDouble) < 0.3)(cleanUnused())
          _       <- logger.info(s"S3Cache: ${(spaceBeforeClean - storageDir.getFreeSpace) / 1000 -> "cleaned"}KB.")
        } yield ()
      }
    }
  }
}
