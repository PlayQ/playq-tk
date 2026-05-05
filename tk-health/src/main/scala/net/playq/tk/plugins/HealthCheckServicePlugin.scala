package net.playq.tk.plugins

import izumi.distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import net.playq.tk.health.config.HealthCheckServiceConfig
import net.playq.tk.health.{HealthCheckService, HealthChecker}
import zio.IO

object HealthCheckServicePlugin extends PluginDef with ConfigModuleDef {
  many[HealthChecker[IO]]
  make[HealthCheckService[IO]].fromResource[HealthCheckService.AsyncChecker[IO]]
  makeConfig[HealthCheckServiceConfig]("health")
}
