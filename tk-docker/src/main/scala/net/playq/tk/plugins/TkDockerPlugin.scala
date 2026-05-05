package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import zio.Task

object TkDockerPlugin extends PluginDef {
  include(TkDockerModule[Task])
}
