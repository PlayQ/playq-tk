package net.playq.tk.plugins

import distage.plugins.PluginDef
import net.playq.tk.util.await.TkWaiter
import zio.IO

object TkWaiterPlugin extends PluginDef {
  make[TkWaiter[IO]].from[TkWaiter.RandomSleep[IO]]
}
