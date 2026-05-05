package net.playq.tk.health.config

import scala.concurrent.duration.FiniteDuration

final case class HealthCheckServiceConfig(
  updateInterval: FiniteDuration
)
