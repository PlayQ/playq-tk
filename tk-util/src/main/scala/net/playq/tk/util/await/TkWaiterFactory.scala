package net.playq.tk.util.await

import izumi.functional.bio.{F, Temporal2}
import logstage.LogIO2
import logstage.LogIO2.log

import scala.concurrent.duration.FiniteDuration

final class TkWaiterFactory[F[+_, +_]: Temporal2: LogIO2] {
  def apply(constant: FiniteDuration): TkWaiter[F] = new TkWaiter[F] {
    override def sleep(waitMin: FiniteDuration, waitMax: FiniteDuration): F[Nothing, Unit] = {
      log.info(s"TEST MODE: Sleeping for $constant time") *>
      F.sleep(constant)
    }
  }
}
