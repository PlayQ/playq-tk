package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import net.playq.tk.metrics.modules.TestMetricsModule
import zio.IO

object TestMetricsPlugin extends PluginDef {
  include(TestMetricsModule[IO])
}
