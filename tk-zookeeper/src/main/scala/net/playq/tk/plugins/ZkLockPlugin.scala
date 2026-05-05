package net.playq.tk.plugins

import izumi.distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import net.playq.tk.zookeeper.config.ZookeeperLockConfig

object ZkLockPlugin extends PluginDef {
  include(ZkLockPlugin.config)

  object config extends ConfigModuleDef {
    makeConfig[ZookeeperLockConfig]("zookeeper")
  }
}
