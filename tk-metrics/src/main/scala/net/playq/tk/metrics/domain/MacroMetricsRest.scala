package net.playq.tk.metrics.domain

import net.playq.tk.metrics.base.MetricDef
import net.playq.tk.metrics.base.MetricDef.MetricMeter
import net.playq.tk.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricRestMeter, MacroMetricRestTimer}

import scala.language.experimental.macros

object MacroMetricsRest {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"legacy/$label/errors"
    override def createMetrics(role: String, label: String): List[MetricDef] = {
      super.createMetrics(role, label) ++
      List(200, 401, 403, 404, 500, 400)
        .map(code => MetricMeter(role, s"$label-http-$code", 0): MetricDef)
    }
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsRest.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsRest.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"legacy/$label/time"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsRest.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsRest.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricRestMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricRestTimer[S] = Timer.empty[S]
  }
}
