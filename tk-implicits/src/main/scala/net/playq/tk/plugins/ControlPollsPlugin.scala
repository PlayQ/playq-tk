package net.playq.tk.plugins

import distage.{ModuleDef, TagKK}
import izumi.distage.plugins.PluginDef
import izumi.functional.bio.{Clock2, Temporal2}
import net.playq.tk.control.fiber.FiberEffectRunner.FiberEffects
import net.playq.tk.concurrent.LoopTimer
import net.playq.tk.control.fiber.FiberEffectRunner
import zio.IO

import scala.concurrent.duration.DurationInt

object ControlPollsPlugin extends PluginDef {
  include(ControlPollsPlugin.module[IO])

  private def module[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[FiberEffects[F]].fromResource[FiberEffectRunner[F]]
    make[LoopTimer[F]].named("minute").from {
      (clock: Clock2[F], t: Temporal2[F]) =>
        LoopTimer.fromClock(clock, 1.minute)(t)
    }
  }
}
