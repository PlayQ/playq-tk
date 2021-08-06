package net.playq.tk.metrics.domain

import net.playq.tk.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricSagemakerMeter, MacroMetricSagemakerTimer}

import scala.language.experimental.macros

object MacroMetricsSagemaker {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"sagemaker/query-exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsSagemaker.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsSagemaker.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"sagemaker/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsSagemaker.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsSagemaker.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricSagemakerMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricSagemakerTimer[S] = Timer.empty[S]
  }
}
