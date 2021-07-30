package net.playq.tk.metrics.domain

import net.playq.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricCostMeter, MacroMetricCostTimer}

import scala.language.experimental.macros

object MacroMetricsCost {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"cost/query-exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsCost.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsCost.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"cost/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsCost.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsCost.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricCostMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricCostTimer[S] = Timer.empty[S]
  }
}
