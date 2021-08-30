package net.playq.tk.implicits

import cats.effect
import zio.IO

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS, TimeUnit}

object TimerInstances {
  implicit def timer[E](implicit zioClock: zio.clock.Clock.Service): cats.effect.Timer[IO[E, _]] = new effect.Timer[IO[E, _]] {
    override def clock: effect.Clock[IO[E, _]] = new effect.Clock[IO[E, _]] {
      override def monotonic(unit: TimeUnit): IO[E, Long] =
        zioClock.nanoTime.map(unit.convert(_, NANOSECONDS))

      override def realTime(unit: TimeUnit): IO[E, Long] =
        zioClock.currentTime(unit)
    }

    override def sleep(duration: FiniteDuration): IO[E, Unit] =
      zioClock.sleep(zio.duration.Duration.fromNanos(duration.toNanos))
  }
}
