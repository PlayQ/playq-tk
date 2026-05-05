package net.playq.tk.synchro

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2, Primitives2, Semaphore2, Temporal2}
import net.playq.tk.concurrent.LoopTimer
import net.playq.tk.synchro.TkSynchronizer.{FailedToAcquireLock, SynchronizerLock, SynchronizerLockReleaser}

import java.time.ZonedDateTime
import scala.concurrent.duration.FiniteDuration

abstract class TkSynchronizer[F[+_, +_]: IO2] {
  /**
    * This method is unsafe to use as-is, because the heartbeat fiber will leak if the method is interrupted
    * - or if the action using the releaser is interrupted.
    *
    * Use [[resource]] or other methods below. Avoid unprotecting this (use [[createLock]] instead). Only use within F.bracket or F.uninterruptible.
    */
  protected def tryAcquire(lockPath: String, await: FiniteDuration): F[Nothing, Option[SynchronizerLockReleaser[F]]]

  /**
    * This method is unsafe to use as-is, because the heartbeat fiber will leak if the method is interrupted
    * - or if the action using the releaser is interrupted.
    *
    * Use [[resource]] or other methods below. Avoid unprivating this (use [[createLock]] instead). Only use within F.bracket or F.uninterruptible.
    */
  private def acquireOrFail(lockPath: String, await: FiniteDuration): F[FailedToAcquireLock, SynchronizerLockReleaser[F]] = {
    tryAcquire(lockPath, await).fromOption(FailedToAcquireLock(lockPath))
  }

  final def createLock(lockPath: String): SynchronizerLock[F] = new SynchronizerLock[F] {
    /** Only use within F.bracket or F.uninterruptible */
    override def acquire(await: FiniteDuration): F[Nothing, Option[SynchronizerLockReleaser[F]]] = {
      tryAcquire(lockPath, await)
    }
  }

  final def resource(lockPath: String, await: FiniteDuration): Lifecycle[F[Nothing, _], Boolean] = {
    Lifecycle.make(tryAcquire(lockPath, await))(_.fold(F.unit)(_.release)).map(_.isDefined)
  }

  final def resourceOrFail(lockPath: String, await: FiniteDuration): Lifecycle[F[FailedToAcquireLock, _], Unit] = {
    Lifecycle.make(acquireOrFail(lockPath, await))(_.release).void
  }

  final def synchronizedLoopTimerAction[E, A](timer: LoopTimer[F], lockPath: String, await: FiniteDuration)(eff: ZonedDateTime => F[E, A]): F[E, Option[A]] = {
    F.bracket(tryAcquire(lockPath, await))(_.fold(F.unit)(_.release)) {
      case Some(_) => timer.pollZoned().flatMap(eff(_).map(Some(_)))
      case None    => F.pure(None)
    }
  }

  final def trySynchronized[E, A](lockPath: String, await: FiniteDuration)(eff: F[E, A]): F[E, Option[A]] = {
    F.bracket(tryAcquire(lockPath, await))(_.fold(F.unit)(_.release)) {
      case Some(_) => eff.map(Some(_))
      case None    => F.pure(None)
    }
  }

  final def synchronizedOrFail[E, A](lockPath: String, await: FiniteDuration)(eff: F[E, A])(implicit ev: FailedToAcquireLock <:< E): F[E, A] = {
    F.bracket(F.widenError(acquireOrFail(lockPath, await)))(_.release) {
      _ => eff
    }
  }

}

object TkSynchronizer {

  trait SynchronizerLock[F[_, _]] {
    /** Only use within F.bracket or F.uninterruptible */
    def acquire(await: FiniteDuration): F[Nothing, Option[SynchronizerLockReleaser[F]]]
  }

  trait SynchronizerLockReleaser[F[_, _]] {
    def release: F[Nothing, Unit]
  }

  final case class FailedToAcquireLock(lock: String) extends RuntimeException(s"Failed to acquire lock: $lock")

  final class Dummy[F[+_, +_]: IO2: Temporal2: Primitives2]
    extends Lifecycle.LiftF[F[Throwable, _], TkSynchronizer[F]](for {
      mutexMap <- F.mkRefM(Map.empty[String, Semaphore2[F]])
    } yield new TkSynchronizer[F] {
      override def tryAcquire(lockPath: String, await: FiniteDuration): F[Nothing, Option[SynchronizerLockReleaser[F]]] = {
        for {
          mm   <- mutexMap.update(s => if (s.contains(lockPath)) F.pure(s) else F.mkSemaphore(1).map(s + lockPath.->(_)))
          mutex = mm(lockPath)
          res  <- mutex.acquire.timeout(await).map {
            _.map {
              _ =>
                new SynchronizerLockReleaser[F] {
                  override def release: F[Nothing, Unit] = mutex.release
                }
            }
          }
        } yield res
      }
    })

  final class Empty[F[+_, +_]: IO2] extends TkSynchronizer[F] {
    override def tryAcquire(lockPath: String, await: FiniteDuration): F[Nothing, Option[SynchronizerLockReleaser[F]]] = F.pure {
      Some(
        new SynchronizerLockReleaser[F] {
          override def release: F[Nothing, Unit] = F.unit
        }
      )
    }
  }
}
