package net.playq.tk

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{Clock1, Clock2, Entropy1, IO2}
import izumi.fundamentals.platform.functional.Identity
import net.playq.tk.clock.ClockShifter
import net.playq.tk.envs.ShiftedClockEnv
import net.playq.tk.test.{TkTestBaseCtx, WithDummy}
import zio.IO

final class ClockShiftTest extends ClockShiftTestBase[IO] with ShiftedClockEnv[IO] with WithDummy

abstract class ClockShiftTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, ClockShiftTest.Ctx[F]] {
  "Clock shifter" must {
    "shift clock impurely" in bound {
      ctx =>
        import ctx.*

        val targetShift = entropy.nextLong().abs
        val first       = testClock.nowZoned()
        for {
          _     <- shifter.shiftOffset(targetShift)
          second = testClock.nowZoned()
          st     = first.toInstant.toEpochMilli
          end    = second.toInstant.toEpochMilli
          _     <- assertIO(!((end - st) < targetShift))
        } yield ()
    }

    "shift clock in IO" in bound {
      ctx =>
        import ctx.*
        val targetShift = entropy.nextLong().abs
        for {
          first  <- clockIO.nowZoned()
          _      <- shifter.shiftOffset(targetShift)
          second <- clockIO.nowZoned()
          st      = first.toInstant.toEpochMilli
          end     = second.toInstant.toEpochMilli
          _      <- assertIO(!((end - st) < targetShift))
        } yield ()

    }
  }
}

object ClockShiftTest {
  final case class Ctx[F[+_, +_]](
    testClock: Clock1[Identity],
    entropy: Entropy1[Identity],
    clockIO: Clock2[F],
    shifter: ClockShifter[F],
  )
}
