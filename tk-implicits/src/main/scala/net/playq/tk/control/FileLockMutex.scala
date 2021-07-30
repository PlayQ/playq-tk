package net.playq.tk.control

import izumi.functional.bio.{Async2, F, Temporal2}
import logstage.LogIO2

import java.io.File
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler, FileLock, OverlappingFileLockException}
import java.nio.file.StandardOpenOption
import scala.concurrent.duration._

object FileLockMutex {

  def withLocalMutex[F[+_, +_]: Async2: Temporal2, A](
    filename: String,
    waitFor: FiniteDuration,
    maxAttempts: Int,
  )(effect: F[Throwable, A]
  )(implicit logger: LogIO2[F]
  ): F[Throwable, A] = {
    def retryOnFileLock(eff: F[Throwable, FileLock], attempts: Int = 0): F[Throwable, Option[FileLock]] = {
      logger.debug(s"Attempt ${attempts -> "num"} out of $maxAttempts to acquire lock for $filename.") *>
      eff.map(Option(_)).catchSome {
        case _: OverlappingFileLockException =>
          if (attempts < maxAttempts) {
            F.sleep(waitFor).flatMap(_ => retryOnFileLock(eff, attempts + 1))
          } else {
            logger.warn(s"Cannot acquire lock for image $filename after $attempts. This may lead to creation of a new container duplicate.")
            F.pure(None)
          }
      }
    }

    def createChannel: F[Nothing, AsynchronousFileChannel] = F.sync {
      val tmpDir         = System.getProperty("java.io.tmpdir")
      val file           = new File(s"$tmpDir/$filename.tmp")
      val newFileCreated = file.createNewFile()
      if (newFileCreated) file.deleteOnExit()
      AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE)
    }

    def acquireLock(channel: AsynchronousFileChannel): F[Throwable, Option[FileLock]] = {
      retryOnFileLock {
        F.async[Throwable, FileLock] {
          cb =>
            val handler = new CompletionHandler[FileLock, Unit] {
              override def completed(result: FileLock, attachment: Unit): Unit = cb(Right(result))
              override def failed(exc: Throwable, attachment: Unit): Unit      = cb(Left(exc))
            }
            channel.lock((), handler)
        }.sandboxToThrowable
      }
    }

    F.bracket(
      acquire = createChannel
    )(release = channel => F.syncThrowable(channel.close()).catchAll(_ => F.unit))(
      use = {
        channel =>
          F.bracket(
            acquire = acquireLock(channel)
          )(release = {
            case Some(lock) => F.sync(lock.close())
            case None       => F.unit
          })(use = _ => effect)
      }
    )
  }
}
