package net.playq.tk

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{Clock2, IO2}
import izumi.functional.mono.{Clock, Entropy}
import izumi.fundamentals.platform.functional.Identity
import net.playq.tk.clock.ClockShifter
import net.playq.tk.envs.ShiftedClockEnv
import net.playq.tk.test.{TkTestBaseCtx, WithDummy}
import zio.IO

final class ClockShiftTest extends ClockShiftTestBase[IO] with ShiftedClockEnv[IO] with WithDummy

abstract class ClockShiftTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, ClockShiftTest.Ctx[F]] {
  "Closk shifter" must {
    "shift clock impurely" in scopeIO {
      ctx =>
        import ctx.*

        val targetShift = entropy.nextLong().abs
        val first       = testClock.now()
        for {
          _     <- shifter.shiftOffset(targetShift)
          second = testClock.now()
          st     = first.toInstant.toEpochMilli
          end    = second.toInstant.toEpochMilli
          _     <- assertIO(!((end - st) < targetShift))
        } yield ()
    }

    "shift clock in IO" in scopeIO {
      ctx =>
        import ctx.*
        val targetShift = entropy.nextLong().abs
        for {
          first  <- clockIO.now()
          _      <- shifter.shiftOffset(targetShift)
          second <- clockIO.now()
          st      = first.toInstant.toEpochMilli
          end     = second.toInstant.toEpochMilli
          _      <- assertIO(!((end - st) < targetShift))
        } yield ()

    }
  }
}

object ClockShiftTest {
  final case class Ctx[F[+_, +_]](
    testClock: Clock[Identity],
    entropy: Entropy[Identity],
    clockIO: Clock2[F],
    shifter: ClockShifter[F],
  )
}
