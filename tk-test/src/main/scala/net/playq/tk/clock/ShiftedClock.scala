package net.playq.tk.clock

import izumi.functional.bio.Clock1
import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.fundamentals.platform.functional.Identity

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, OffsetDateTime, ZoneId, ZonedDateTime}

final class ShiftedClock[F[+_, +_]](
  shifter: ClockShifter[F]
) extends Clock1[Identity] {
  override def epoch: Identity[Long]                                 = now().toInstant.toEpochMilli
  override def now(accuracy: ClockAccuracy): Identity[ZonedDateTime] = {
    nowZoned(accuracy)
  }
  override def nowZoned(accuracy: ClockAccuracy, zone: ZoneId): Identity[ZonedDateTime] = {
    Clock1.Standard.nowZoned(accuracy).plus(shifter.getShiftOffset, ChronoUnit.MILLIS)
  }
  override def nowLocal(accuracy: ClockAccuracy, zone: ZoneId): Identity[LocalDateTime] = {
    Clock1.Standard.nowLocal(accuracy).plus(shifter.getShiftOffset, ChronoUnit.MILLIS)
  }
  override def nowOffset(accuracy: ClockAccuracy, zone: ZoneId): Identity[OffsetDateTime] = {
    Clock1.Standard.nowOffset(accuracy, zone).plus(shifter.getShiftOffset, ChronoUnit.MILLIS)
  }
  override def monotonicNano: Identity[Long] = {
    Clock1.Standard.monotonicNano + (shifter.getShiftOffset * 1000000)
  }
}
