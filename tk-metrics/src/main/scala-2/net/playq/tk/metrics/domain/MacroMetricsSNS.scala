package net.playq.tk.metrics.domain

import net.playq.tk.metrics.{MacroMetricSNSMeter, MacroMetricSNSTimer}
import net.playq.tk.metrics.macrodefs.MacroMetricBase
import scala.language.experimental.macros

object MacroMetricsSNS {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String                                   = s"sns/exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsSNS.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsSNS.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String                                   = s"sns/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsSNS.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsSNS.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricSNSMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricSNSTimer[S] = Timer.empty[S]
  }
}
