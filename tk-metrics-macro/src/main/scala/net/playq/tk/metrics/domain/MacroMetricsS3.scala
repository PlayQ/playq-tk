package net.playq.tk.metrics.domain

import net.playq.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricS3Meter, MacroMetricS3Timer}

import scala.language.experimental.macros

object MacroMetricsS3 {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"s3/query-exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsS3.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsS3.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"s3/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsS3.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsS3.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricS3Meter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricS3Timer[S] = Timer.empty[S]
  }
}
