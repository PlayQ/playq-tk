package net.playq.tk.plugins

import izumi.distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import izumi.fundamentals.platform.integration.PortCheck
import net.playq.tk.test.checkers.PortCheckCfg

object PortCheckPlugin extends PluginDef with ConfigModuleDef {
  makeConfig[PortCheckCfg]("portChecks")
  make[PortCheck].from {
    cfg: PortCheckCfg =>
      new PortCheck(cfg.connectionTimeout)
  }
}
