package net.playq.tk.util.await

import izumi.functional.bio.{Async2, Entropy2, F, Monad2, Temporal2}

import scala.concurrent.duration.*

final case class PollInterval(
  minAwait: FiniteDuration,
  maxAwait: FiniteDuration,
) {
  def getAwaitTime[F[+_, +_]: Monad2: Entropy2]: F[Nothing, Duration] = {
    Some(maxAwait.toSeconds - minAwait.toSeconds)
      .filter(_ > 0L)
      .fold(F.pure(0L))(F.entropy.nextLong)
      .map(_ + minAwait.toSeconds)
      .map(_.seconds)
  }
  def await[F[+_, +_]: Monad2: Temporal2: Entropy2]: F[Nothing, Unit] = {
    getAwaitTime[F].flatMap(F.sleep)
  }
  def awaitWithInterrupt[F[+_, +_]: Async2: Temporal2: Entropy2](interruptOn: F[Nothing, Unit]): F[Nothing, Unit] = {
    getAwaitTime[F].flatMap(F.sleep).race(interruptOn)
  }
}
