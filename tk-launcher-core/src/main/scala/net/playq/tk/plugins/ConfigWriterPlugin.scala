package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import izumi.distage.roles.bundled.{ConfigWriter, Help}
import izumi.distage.roles.model.definition.RoleModuleDef
import zio.IO

object ConfigWriterPlugin extends PluginDef with RoleModuleDef {
  makeRole[ConfigWriter[IO[Throwable, _]]]
  makeRole[Help[IO[Throwable, _]]]
}
