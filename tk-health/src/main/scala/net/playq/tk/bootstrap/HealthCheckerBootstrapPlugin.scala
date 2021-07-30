package net.playq.tk.bootstrap

import distage.AutoSetModule
import izumi.distage.plugins.BootstrapPluginDef
import net.playq.tk.health.HealthChecker
import zio.IO

object HealthCheckerBootstrapPlugin extends BootstrapPluginDef {
  include(module)

  def module: AutoSetModule = new AutoSetModule {
    register[HealthChecker[IO]]
  }
}
