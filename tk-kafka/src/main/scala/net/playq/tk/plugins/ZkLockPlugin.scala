package net.playq.tk.plugins

import distage.{ModuleDef, TagKK}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginDef
import net.playq.tk.kafka.KafkaTopicZkLock
import net.playq.tk.kafka.config.ZookeeperLockConfig
import zio.IO

object ZkLockPlugin extends PluginDef {
  include(ZkLockPlugin.config)
  include(ZkLockPlugin.prod[IO])
  include(ZkLockPlugin.dummy[IO])

  def prod[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Prod)
    make[KafkaTopicZkLock[F]].from[KafkaTopicZkLock.Impl[F]]
  }

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Dummy)
    make[KafkaTopicZkLock[F]].fromResource[KafkaTopicZkLock.Dummy[F]]
  }

  object config extends ConfigModuleDef {
    makeConfig[ZookeeperLockConfig]("zookeeper")
  }
}
