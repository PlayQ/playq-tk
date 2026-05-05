package net.playq.tk.concurrent

import izumi.functional.bio.Fiber2
import FiberWatchdog.FiberTracker
import zio.*

import java.lang.ref.WeakReference

trait FiberWatchdog[F[+_, +_]] {
  def forkWithWatchdog[E, A](eff: F[E, A]): F[Nothing, (WeakReference[Fiber2[F, E, A]], FiberTracker[F])]
}

object FiberWatchdog {
  final class Impl extends FiberWatchdog[IO] {
    override def forkWithWatchdog[E, A](eff: IO[E, A]): IO[Nothing, (WeakReference[Fiber2[IO, E, A]], FiberTracker[IO])] = {
      for {
        interrupted  <- Ref.make(true)
        fiberRuntime <- eff.interruptible.onExit {
          case Exit.Success(_) => ZIO.unit
          case Exit.Failure(_) => ZIO.descriptorWith(d => ZIO.when(d.interrupters.isEmpty)(interrupted.set(false)))
        }.forkDaemon
      } yield {
        val fiber   = Fiber2.fromZIO(interrupted.get)(fiberRuntime)
        val ref     = new WeakReference(fiberRuntime)
        val tracker = new FiberTracker[IO] {
          override def isInaccessible: IO[Nothing, Boolean] = ZIO.succeed(ref.get() eq null)
        }
        new WeakReference(fiber) -> tracker
      }
    }
  }

  implicit final class FiberWatchdogSyntax[F[+_, +_], E1, A1](private val eff: F[E1, A1]) extends AnyVal {
    def forkWithWatchdog(implicit FW: FiberWatchdog[F]): F[Nothing, (WeakReference[Fiber2[F, E1, A1]], FiberTracker[F])] = {
      FW.forkWithWatchdog(eff)
    }
  }

  trait FiberTracker[F[+_, +_]] {
    def isInaccessible: F[Nothing, Boolean]
  }
}
