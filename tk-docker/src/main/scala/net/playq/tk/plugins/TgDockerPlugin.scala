package net.playq.tk.plugins

import distage.{ModuleDef, TagK}
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.plugins.PluginDef
import net.playq.tk.docker.TkDefaultNetwork
import zio.Task

object TgDockerPlugin extends PluginDef {
  include(TgDockerPlugin.module[Task])

  def module[F[_]: TagK]: ModuleDef = new ModuleDef {
    include(DockerSupportModule[F])
    make[TkDefaultNetwork.Network].fromResource(TkDefaultNetwork.make[F])
  }
}
