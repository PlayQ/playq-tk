package net.playq.tk.util.await

import izumi.functional.bio.{Entropy2, F, Monad2, Primitives2, Temporal2}
import logstage.LogIO2
import logstage.LogIO2.log

import scala.concurrent.duration.FiniteDuration

trait TkWaiter[F[_, _]] {
  def sleep(pollInterval: PollInterval): F[Nothing, Unit]
}

object TkWaiter {

  final class RandomSleep[F[+_, +_]: Monad2: Temporal2: LogIO2: Entropy2] extends TkWaiter[F] {
    override def sleep(pollInterval: PollInterval): F[Nothing, Unit] = {
      for {
        prewaitTime <- pollInterval.getAwaitTime[F]
        _           <- log.info(s"Lock cycle: sleeping for $prewaitTime seconds from range ${pollInterval.minAwait}-${pollInterval.maxAwait}")
        _           <- F.sleep(prewaitTime)
      } yield ()
    }
  }

  final class TestFactory[F[+_, +_]: Monad2: Temporal2: LogIO2: Primitives2] {

    def constantSleep(constant: FiniteDuration): TkWaiter[F] = {
      _ =>
        log.info(s"TEST MODE: Sleeping for $constant time") *>
        F.sleep(constant)
    }

    def waitingForSignal: F[Nothing, (F[Nothing, Unit], TkWaiter[F])] = {
      for {
        initLatch    <- F.mkLatch
        currentLatch <- F.mkRef(initLatch)

        signalWakeUp  = currentLatch.get.flatMap(_.succeed(()).void)
        waitForSignal = for {
          _        <- currentLatch.get.flatMap(_.await)
          newLatch <- F.mkLatch
          _        <- currentLatch.set(newLatch)
        } yield ()
      } yield (signalWakeUp, (_: PollInterval) => waitForSignal)
    }

  }

}
