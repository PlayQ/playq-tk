package net.playq.tk.metrics.domain

import net.playq.metrics.base.MetricDef
import net.playq.metrics.base.MetricDef.MetricMeter
import net.playq.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricLegacyRestMeter, MacroMetricLegacyRestTimer}

import scala.language.experimental.macros

object MacroMetricsLegacyRest {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"legacy/$label/errors"
    override def createMetrics(role: String, label: String): List[MetricDef] = {
      super.createMetrics(role, label) ++
      List(200, 401, 403, 404, 500, 400)
        .map(code => MetricMeter(role, s"$label-http-$code", 0): MetricDef)
    }
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsLegacyRest.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsLegacyRest.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"legacy/$label/time"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsLegacyRest.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsLegacyRest.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricLegacyRestMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricLegacyRestTimer[S] = Timer.empty[S]
  }
}
