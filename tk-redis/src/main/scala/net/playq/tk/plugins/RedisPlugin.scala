package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import distage.{TagK, TagKK}
import izumi.functional.bio.Clock1
import izumi.fundamentals.platform.functional.Identity
import net.playq.tk.redis.RedisComponent
import net.playq.tk.redis.docker.*
import zio.IO

object RedisPlugin extends PluginDef {
  include(RedisPlugin.module[IO])

  def module[F[+_, +_]: TagKK](implicit ev: TagK[F[Throwable, _]]): ConfigModuleDef = new ConfigModuleDef {
    /* use personalized redis clock to not memoize clock in testkit (if clocks is memoized we can not use shifted clocks) */
    make[Clock1[Identity]].named("redis").fromValue(Clock1.Standard)
    make[RedisComponent[F]].fromResource[RedisComponent.Resource[F]]
    include(RedisDocker.module[F[Throwable, _]]("redis"))
//    include(RedisClusterDocker.module[F[Throwable, _]]("redis"))
  }
}
