package net.playq.tk.cache

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2, Monad2, Primitives2, Semaphore2, Temporal2}
import logstage.LogIO2
import net.playq.tk.cache.CacheBase.CacheRemovalListener
import net.playq.tk.cache.FilesCache.FileWithLock
import net.playq.tk.util.ClosableFile

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import scala.annotation.unused

abstract class FilesCache[F[+_, +_]: IO2: Primitives2: LogIO2, Key <: AnyRef](
  cacheConfig: CacheConfig
) {
  // queue for files deletion
  private[this] final val cleanupTasks = new ConcurrentLinkedQueue[FileWithLock[F, Key]]()
  // guava based files cache
  private[this] final val cache = new CacheBase[F, Throwable, Key, FileWithLock[F, Key]](cacheConfig) {
    // do not use soft references as files are stored on disk and can not be garbage collected
    override protected def useSoftReferences: Boolean                         = false
    override protected def load(key: Key): F[Throwable, FileWithLock[F, Key]] = {
      for {
        directory    <- loadFile(key)
        fileWithLock <- FileWithLock.create(key, directory)
      } yield fileWithLock
    }
    override protected def removalListener: Option[CacheRemovalListener[Key, FileWithLock[F, Key]]] = Some {
      CacheRemovalListener.Sync {
        case (_, fileWithLock) =>
          Option(fileWithLock).foreach(cleanupTasks.offer)
      }
    }
  }

  protected def loadFile(key: Key): F[Throwable, ClosableFile]

  def get(key: Key): Lifecycle[F[Throwable, _], File] = {
    for {
      file <- Lifecycle.liftF(cache.get(key))
      _    <- file.lockResource()
    } yield file.file.file
  }

  def reloadAndGet(key: Key): Lifecycle[F[Throwable, _], File] = {
    for {
      _   <- Lifecycle.liftF(cache.invalidate(key))
      res <- get(key)
    } yield res
  }

  def invalidate(key: Key): F[Nothing, Unit] = cache.invalidate(key)

  def invalidateAll(): F[Nothing, Unit] = cache.invalidateAll()

  def cleanup(): F[Nothing, Unit] = {
    F.iterateWhile {
      F.sync(Option(cleanupTasks.poll())).flatMap {
        case Some(fileWithLock) => fileWithLock.awaitAndClose().tap(_ => cleanupAction(fileWithLock.key)).as(true)
        case None               => F.pure(false)
      }
    }(identity).void
  }

  protected def cleanupAction(@unused key: Key): F[Nothing, Unit] = F.unit
}

object FilesCache {
  final case class FileWithLock[F[+_, +_], Key](
    key: Key,
    file: ClosableFile,
    lock: Semaphore2[F],
  ) {
    def awaitAndClose()(implicit log: LogIO2[F], ev: IO2[F]): F[Nothing, Unit] = {
      for {
        _ <- log.debug(s"Files Cache: Cached directory $key deletion: acquiring resource.")
        _ <- lock.acquireN(Long.MaxValue)
        _ <- log.debug(s"Files Cache: Cached directory $key deletion: started.")
        _ <- F.sync(file.close())
        _ <- lock.releaseN(Long.MaxValue)
        _ <- log.debug(s"Files Cache: Cached directory $key deletion: successful.")
      } yield ()
    }
    def lockResource(): Lifecycle[F[Throwable, _], Unit] = Lifecycle.make(lock.acquire)(_ => lock.release)
  }

  object FileWithLock {
    def create[F[+_, +_]: Primitives2: Monad2, Key](key: Key, file: ClosableFile): F[Nothing, FileWithLock[F, Key]] = {
      F.mkSemaphore(Long.MaxValue).map(FileWithLock(key, file, _))
    }
  }
}
