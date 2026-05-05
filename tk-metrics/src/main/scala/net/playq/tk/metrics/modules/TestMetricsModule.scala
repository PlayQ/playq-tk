package net.playq.tk.metrics.modules

import distage.TagKK
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Mode
import net.playq.tk.metrics.{Metrics, MetricsExtractor, MetricsSync}

final class TestMetricsModule[F[+_, +_]: TagKK] extends ModuleDef {
  tag(Mode.Test)

  make[Metrics[F]].from[Metrics.Empty[F]]
  make[MetricsSync].from[Metrics.EmptySync]
  make[MetricsExtractor]
}

object TestMetricsModule {
  def apply[F[+_, +_]: TagKK]: TestMetricsModule[F] = new TestMetricsModule[F]
}
