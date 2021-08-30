package net.playq.tk.aws.s3

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, Clock2, F, Fork2, Primitives2, Temporal2}
import logstage.LogIO2
import net.playq.tk.util.ManagedFile
import net.playq.tk.control.FileLockMutex
import org.apache.commons.io.FileUtils

import java.time.ZonedDateTime
import scala.concurrent.duration.{FiniteDuration, _}

trait S3BucketCached[F[+_, +_], BucketId <: S3BucketId] {
  def acquire(key: String): F[Throwable, ManagedFile]
  def release(key: String): F[Nothing, Unit]
  def evict(key: String): F[Nothing, Unit]
  def reset(): F[Nothing, Unit]
}

object S3BucketCached {
  final case class CacheFilesTTL(
    deleteAfter: FiniteDuration
  )

  final case class CachedFile(managedFile: ManagedFile, createdAt: ZonedDateTime, cleanupAfter: ZonedDateTime, inUse: Int = 1) {
    def acquire: CachedFile = copy(inUse = inUse + 1)
    def release: CachedFile = copy(inUse = math.max(inUse - 1, 0))
    def expired(now: ZonedDateTime): Boolean = {
      unused && cleanupAfter.isBefore(now)
    }
    def unused: Boolean = {
      inUse <= 0
    }
  }

  final class Default[F[+_, +_]: Async2: Fork2: Primitives2: Temporal2, BucketId <: S3BucketId](
    bucket: S3Bucket[F, BucketId],
    clock: Clock2[F],
    config: CacheFilesTTL,
  )(implicit val logger: LogIO2[F]
  ) extends Lifecycle.NoClose[F[Throwable, _], S3BucketCached[F, BucketId]] {
    override def acquire: F[Throwable, S3BucketCached[F, BucketId]] = {
      for {
        keyToFile        <- F.mkRef(Map.empty[String, CachedFile])
        storageDir       <- F.sync(FileUtils.getTempDirectory)
        startingFreeSpace = storageDir.getFreeSpace
      } yield {
        new S3BucketCached[F, BucketId] {
          /**
            * Operations on [[keyToFile]] is atomic, but anyway, we can not execute effect under [[keyToFile.modify]].
            * That's why we need to add [[FileLockMutex]] on file to avoid races.
            *
            * It's still safe to not to use mutex at all,
            * but in case of parallel file creation we might create 2 files with the same content, and save only one [[CachedFile]] instance.
            * In case of duplicated file creation we can lose file from cached scope. We may use Set instead of map for state,
            * but it will be slower in common cases. Also, time consumed during mutex awaiting will be the same as for file downloading so it should be pretty fast.
            */
          override def acquire(key: String): F[Throwable, ManagedFile] = {
            for {
              _       <- cleanupCache().fork
              hashcode = key.hashCode.toString
              _       <- logger.info(s"Trying to acquire: $key")
              res <- FileLockMutex.withLocalMutex(hashcode, 100.millis, 1000) {
                keyToFile.get.map(_.get(key)).flatMap {
                  case None =>
                    for {
                      _            <- logger.info(s"S3Cache: downloaded and acquired file: $key")
                      file         <- bucket.downloadToFile(key).unsafeGet()
                      now          <- clock.now()
                      cacheInternal = CachedFile(file, now, now.plusSeconds(config.deleteAfter.toSeconds))
                      _            <- keyToFile.update(_ ++ Map(key -> cacheInternal))
                    } yield file
                  case Some(cacheInternal) =>
                    for {
                      _ <- logger.info(s"S3Cache: acquired file: $key")
                      _ <- keyToFile.update(_ ++ Map(key -> cacheInternal.acquire))
                    } yield cacheInternal.managedFile
                }
              }
            } yield res
          }

          override def release(key: String): F[Nothing, Unit] = {
            for {
              _ <- logger.info(s"S3Cache: file released: $key")
              _ <- keyToFile.update_(_.updatedWith(key)(_.map(_.release)))
            } yield ()
          }

          override def evict(key: String): F[Nothing, Unit] = {
            for {
              cached <- keyToFile.modify {
                state =>
                  state.get(key) match {
                    case Some(cached) if cached.unused => Some(cached) -> state.removed(key)
                    case _                             => None         -> state
                  }
              }
              _ <- cached match {
                case Some(value) =>
                  F.sync(value.managedFile.close()) *> logger.info(s"S3Cache: file evicted: $key")
                case None =>
                  logger.warn(s"S3Cache: file can not be evicted (still in use or does not persists in cache): $key")
              }
            } yield ()
          }

          override def reset(): F[Nothing, Unit] = {
            for {
              unused <- keyToFile.modify(_.partition(_._2.unused))
              _      <- F.sync(unused.map(_._2.managedFile.close()))
              _      <- logger.info(s"S3Cache: evicted all unused files.")
            } yield ()
          }

          /** We will cleanup cache if there left 30% of available space (available space is a space that was available on start)
            * Firstly we will delete all expired files, then if space still not enough we will delete all unused files.
            * Also, we don't need to add synchronization over file since local state modification is atomic and even each files location is unique.
            */
          private[this] def cleanupCache(): F[Nothing, Unit] = {
            val spaceBeforeClean = storageDir.getFreeSpace
            F.when((spaceBeforeClean.toDouble / startingFreeSpace.toDouble) < 0.3) {
              for {
                now     <- clock.now()
                expired <- keyToFile.modify(_.partition(_._2.expired(now)))
                _       <- F.sync(expired.map(_._2.managedFile.close()))
                _       <- F.when((storageDir.getFreeSpace.toDouble / startingFreeSpace.toDouble) < 0.3)(reset())
                _       <- logger.info(s"S3Cache: ${(spaceBeforeClean - storageDir.getFreeSpace) / 1000 -> "cleaned"}KB.")
              } yield ()
            }
          }
        }
      }
    }
  }
}
