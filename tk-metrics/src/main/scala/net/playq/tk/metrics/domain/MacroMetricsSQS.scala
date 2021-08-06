package net.playq.tk.metrics.domain

import net.playq.tk.metrics.{MacroMetricSQSMeter, MacroMetricSQSTimer}
import net.playq.tk.metrics.macrodefs.MacroMetricBase

import scala.language.experimental.macros

object MacroMetricsSQS {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"sqs/exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsSQS.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsSQS.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"sqs/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsSQS.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsSQS.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricSQSMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricSQSTimer[S] = Timer.empty[S]
  }
}
