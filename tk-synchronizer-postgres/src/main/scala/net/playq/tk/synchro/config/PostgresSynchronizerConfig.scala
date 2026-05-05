package net.playq.tk.synchro.config

import scala.concurrent.duration.FiniteDuration

final case class PostgresSynchronizerConfig(
  ttl: FiniteDuration,
  acquireRetrySleep: FiniteDuration,
)
