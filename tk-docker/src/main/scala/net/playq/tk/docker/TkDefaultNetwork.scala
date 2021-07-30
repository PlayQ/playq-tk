package net.playq.tk.docker

import izumi.distage.docker.ContainerNetworkDef

object TkDefaultNetwork extends ContainerNetworkDef {
  override def config: Config = Config()
}
