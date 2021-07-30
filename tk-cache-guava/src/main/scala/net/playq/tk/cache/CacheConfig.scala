package net.playq.tk.cache

import scala.concurrent.duration.FiniteDuration

final case class CacheConfig(size: Long, ttl: FiniteDuration)
