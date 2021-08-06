package net.playq.tk.plugins

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Mode
import izumi.distage.plugins.PluginDef
import net.playq.tk.metrics.MetricsExtractor
import net.playq.tk.metrics.modules.DummyMetricsModule
import net.playq.tk.metrics.{MetricRegistryWrapper, MetricsReportingComponent}
import zio.IO

object DummyMetricsPlugin extends PluginDef {
  make[MetricsExtractor]
  include(DummyMetricsModule[IO])
  include(DummyMetricsPlugin.module)

  def module: ModuleDef = new ModuleDef {
    tag(Mode.Test)
    make[MetricRegistryWrapper].fromValue(MetricRegistryWrapper(None))
    make[MetricsReportingComponent].fromResource[MetricsReportingComponent.Dummy]
  }
}
