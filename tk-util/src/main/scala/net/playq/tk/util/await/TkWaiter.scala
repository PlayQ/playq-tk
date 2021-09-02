package net.playq.tk.util.await

import izumi.functional.bio.{Entropy2, F, Monad2, Temporal2}
import logstage.LogIO2

import scala.concurrent.duration.{DurationInt, DurationLong, FiniteDuration}

trait TkWaiter[F[_, _]] {
  final def sleep(waitMin: Int, waitMax: Int): F[Nothing, Unit] = sleep(waitMin.seconds, waitMax.seconds)
  def sleep(waitMin: FiniteDuration, waitMax: FiniteDuration): F[Nothing, Unit]
}

object TkWaiter {
  final class RandomSleep[F[+_, +_]: Monad2: Temporal2](
    log: LogIO2[F],
    entropy: Entropy2[F],
  ) extends TkWaiter[F] {
    override def sleep(waitMin: FiniteDuration, waitMax: FiniteDuration): F[Nothing, Unit] = {
      for {
        prewaitTime <- Some(waitMax.toSeconds - waitMin.toSeconds)
          .filter(_ > 0L)
          .fold(F.pure(0L))(entropy.nextLong)
          .map(offset => waitMin.plus(offset.seconds))

        _ <- log.info(s"Lock cycle: sleeping for $prewaitTime seconds from range $waitMin-$waitMax")
        _ <- F.sleep(prewaitTime)
      } yield ()
    }
  }
}
