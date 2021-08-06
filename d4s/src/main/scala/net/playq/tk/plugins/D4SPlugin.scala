package net.playq.tk.plugins

import d4s.modules.D4SModule
import izumi.distage.plugins.PluginDef
import zio.IO

object D4SPlugin extends PluginDef {
  include(D4SModule[IO])
}
