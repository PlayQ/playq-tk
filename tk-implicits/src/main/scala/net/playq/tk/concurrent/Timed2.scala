package net.playq.tk.concurrent

import izumi.functional.bio.{Clock2, F, Monad2}
import izumi.fundamentals.platform.time.Timed

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object Timed2 {
  def apply[F[+_, +_]: Clock2: Monad2, E, A](f: => F[E, A]): F[E, Timed[A]] = {
    for {
      before <- F.clock.monotonicNano
      res    <- f
      after  <- F.clock.monotonicNano
    } yield Timed(res, FiniteDuration(after - before, TimeUnit.NANOSECONDS))
  }
}
