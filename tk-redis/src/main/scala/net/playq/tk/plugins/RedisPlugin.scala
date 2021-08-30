package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import distage.plugins.PluginDef
import distage.{TagK, TagKK}
import net.playq.tk.redis.RedisComponent
import net.playq.tk.redis.docker.RedisDocker
import zio.IO

object RedisPlugin extends PluginDef {
  include(RedisPlugin.module[IO])

  def module[F[+_, +_]: TagKK](implicit ev: TagK[F[Throwable, _]]): ConfigModuleDef = new ConfigModuleDef {
    make[RedisComponent[F]].fromResource[RedisComponent.Resource[F]]
    include(RedisDocker.module[F[Throwable, _]]("redis"))
  }
}
