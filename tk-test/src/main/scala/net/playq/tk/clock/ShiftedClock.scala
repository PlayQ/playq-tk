package net.playq.tk.clock

import java.time.{LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.time.temporal.ChronoUnit

import izumi.fundamentals.platform.functional.Identity
import izumi.functional.mono.{Clock, ClockAccuracy}

final class ShiftedClock[F[+_, +_]](
  shifter: ClockShifter[F]
) extends Clock[Identity] {
  override def epoch: Identity[Long] = now().toInstant.toEpochMilli
  override def now(accuracy: ClockAccuracy = ClockAccuracy.DEFAULT): Identity[ZonedDateTime] = {
    Clock.Standard.now(accuracy).plus(shifter.getShiftOffset, ChronoUnit.MILLIS)
  }
  override def nowLocal(accuracy: ClockAccuracy): Identity[LocalDateTime]   = now(accuracy).toLocalDateTime
  override def nowOffset(accuracy: ClockAccuracy): Identity[OffsetDateTime] = now(accuracy).toOffsetDateTime
}
