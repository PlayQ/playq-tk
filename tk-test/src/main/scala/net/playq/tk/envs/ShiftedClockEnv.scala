package net.playq.tk.envs

import distage.TagKK
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.functional.mono.Clock
import izumi.fundamentals.platform.functional.Identity
import net.playq.tk.clock.{ClockShifter, ShiftedClock}
import net.playq.tk.test.ModuleOverrides

trait ShiftedClockEnv[F[+_, +_]] extends ModuleOverrides {
  implicit val tagBIO: TagKK[F]

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    make[ClockShifter[F]]
    make[Clock[Identity]].from[ShiftedClock[F]]
  }
}
