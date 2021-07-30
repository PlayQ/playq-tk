package net.playq.tk.clock

import izumi.functional.bio.SyncSafe2

import scala.concurrent.duration._

final class ClockShifter[F[+_, +_]](F: SyncSafe2[F]) {
  private[this] var offset: Long = 0L

  private[clock] def getShiftOffset: Long            = offset
  def shiftOffset(newOffset: Long): F[Nothing, Unit] = F.syncSafe(synchronized { offset = newOffset })
  def reset(): F[Nothing, Unit]                      = F.syncSafe(synchronized { offset = 0L })

  def shiftOffset(finiteDuration: FiniteDuration): F[Nothing, Unit] =
    shiftOffset(finiteDuration.toMillis)
}
