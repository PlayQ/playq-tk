package net.playq.tk.concurrent

import izumi.functional.bio.Fiber2
import FiberWatchdog.FiberTracker
import zio._

import java.lang.ref.WeakReference

trait FiberWatchdog[F[+_, +_]] {
  def forkWithWatchdog[E, A](eff: F[E, A]): F[Nothing, (Fiber2[F, E, A], FiberTracker[F])]
}

object FiberWatchdog {
  final class Impl() extends FiberWatchdog[IO] {
    override def forkWithWatchdog[E, A](eff: IO[E, A]): IO[Nothing, (Fiber2[IO, E, A], FiberTracker[IO])] = {
      eff.interruptible.forkDaemon.map {
        fiberRuntime =>
          val fiber = Fiber2.fromZIO(fiberRuntime).asInstanceOf[Fiber2[IO, E, A]]
          val ref   = new WeakReference(fiberRuntime)
          val tracker = new FiberTracker[IO] {
            override def isInaccessible[E1, A1]: IO[Nothing, Boolean] = UIO(ref.get() eq null)
          }
          fiber -> tracker
      }
    }
  }

  implicit final class FiberWatchdogSyntax[F[+_, +_], E1, A1](eff: F[E1, A1])(implicit FW: FiberWatchdog[F]) {
    def forkWithWatchdog[E, A]: F[Nothing, (Fiber2[F, E1, A1], FiberTracker[F])] = FW.forkWithWatchdog(eff)
  }

  trait FiberTracker[F[+_, +_]] {
    def isInaccessible[E, A]: F[Nothing, Boolean]
  }
}
