package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import net.playq.tk.health.HealthCheckService
import zio.IO

object HealthCheckServicePlugin extends PluginDef {
  make[HealthCheckService[IO]]
}
