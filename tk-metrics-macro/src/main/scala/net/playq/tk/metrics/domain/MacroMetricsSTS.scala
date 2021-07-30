package net.playq.tk.metrics.domain

import net.playq.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricSTSMeter, MacroMetricSTSTimer}

import scala.language.experimental.macros

object MacroMetricsSTS {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"sts/query-exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsSTS.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsSTS.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"sts/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsSTS.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsSTS.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricSTSMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricSTSTimer[S] = Timer.empty[S]
  }
}
