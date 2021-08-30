package net.playq.tk.control.fiber

import izumi.functional.bio.{Entropy2, F, Monad2}

import scala.concurrent.duration.*

final case class PollInterval(private val minAwait: FiniteDuration, private val maxAwait: FiniteDuration) {
  def getAwaitTime[F[+_, +_]: Monad2](implicit entropy: Entropy2[F]): F[Nothing, Duration] =
    Some(maxAwait.toSeconds - minAwait.toSeconds)
      .filter(_ > 0L)
      .fold(F.pure(0L))(entropy.nextLong)
      .map(_ + minAwait.toSeconds)
      .map(_.seconds)
}
