package net.playq.tk.envs

import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.fundamentals.platform.build.ExposedTestScope
import izumi.fundamentals.platform.functional.Identity
import izumi.functional.mono.Clock
import net.playq.tk.clock.{ClockShifter, ShiftedClock}
import net.playq.tk.test.ModuleOverrides
import zio.IO

@ExposedTestScope
trait ShiftedClockEnv extends ModuleOverrides {
  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    make[ClockShifter[IO]]
    make[Clock[Identity]].from[ShiftedClock[IO]]
  }
}
