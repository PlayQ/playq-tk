package net.playq.tk.redis.config

final case class RedisConfig(
  endpoint: String,
  integrationCheck: Boolean,
)
