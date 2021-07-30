package net.playq.tk.plugins

import distage.plugins.PluginDef
import net.playq.tk.util.retry.TkScheduler
import zio.IO

object TkSchedulerPlugin extends PluginDef {
  make[TkScheduler[IO]].from[TkScheduler.Impl[IO]]
}
