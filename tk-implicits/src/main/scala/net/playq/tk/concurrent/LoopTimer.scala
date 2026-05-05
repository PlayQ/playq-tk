package net.playq.tk.concurrent

import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.functional.bio.{BlockingIO2, Clock2, F, IO2, Temporal2}

import java.time.ZonedDateTime
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

trait LoopTimer[F[_, _]] {
  def pollZoned(accuracy: ClockAccuracy = ClockAccuracy.MILLIS): F[Nothing, ZonedDateTime]
  def nowZoned(accuracy: ClockAccuracy  = ClockAccuracy.MILLIS): F[Nothing, ZonedDateTime]
}

object LoopTimer {
  def fromClock[F[+_, +_]: Temporal2](clock2: Clock2[F], pollingInterval: FiniteDuration) = new System(pollingInterval, clock2)
  def fromSeries[F[+_, +_]: IO2](series: Iterable[ZonedDateTime])                         = new Dummy[F](series)

  final class System[F[+_, +_]: Temporal2] private[LoopTimer] (
    pollingInterval: FiniteDuration,
    clock: Clock2[F],
  ) extends LoopTimer[F] {
    override def pollZoned(accuracy: ClockAccuracy): F[Nothing, ZonedDateTime] = F.sleep(pollingInterval) *> nowZoned(accuracy)
    override def nowZoned(accuracy: ClockAccuracy): F[Nothing, ZonedDateTime]  = clock.nowZoned(accuracy)
  }

  @SuppressWarnings(Array("UnsafeTraversableMethods"))
  final class Dummy[F[+_, +_]: IO2] private[LoopTimer] (
    series: Iterable[ZonedDateTime]
  ) extends LoopTimer[F] {
    require(series.nonEmpty, "Empty series in dummy timer...")

    private[this] val latch = new CountDownLatch(1)
    private[this] val state = mutable.Stack(series.toSeq*)

    def await(blocking: BlockingIO2[F]): F[Nothing, Boolean] = {
      blocking.syncBlocking(latch.await(15, TimeUnit.SECONDS)).orTerminate
    }

    @SuppressWarnings(Array("UnsafeTraversableMethods"))
    override def nowZoned(accuracy: ClockAccuracy): F[Nothing, ZonedDateTime] = {
      F.sync(state.headOption.getOrElse(series.last))
    }

    @SuppressWarnings(Array("UnsafeTraversableMethods"))
    override def pollZoned(accuracy: ClockAccuracy): F[Nothing, ZonedDateTime] = F.sync {
      state.synchronized {
        if (state.nonEmpty) {
          state.pop()
        } else {
          latch.countDown()
          series.last
        }
      }
    }
  }
}
