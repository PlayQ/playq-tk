package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import net.playq.tk.health.{HealthCheckService, HealthChecker}
import zio.IO

object HealthCheckServicePlugin extends PluginDef {
  many[HealthChecker[IO]]
  make[HealthCheckService[IO]]
}
