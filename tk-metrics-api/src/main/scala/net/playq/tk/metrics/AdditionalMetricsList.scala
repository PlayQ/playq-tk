package net.playq.tk.metrics

import net.playq.metrics.base.MetricDef

abstract class AdditionalMetricsList {
  val metrics: List[MetricDef]
}

object AdditionalMetricsList {
  def apply(metricsList: List[MetricDef]): AdditionalMetricsList = new AdditionalMetricsList {
    val metrics: List[MetricDef] = metricsList
  }
}
