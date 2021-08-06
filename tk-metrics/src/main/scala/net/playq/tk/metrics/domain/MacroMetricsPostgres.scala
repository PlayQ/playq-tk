package net.playq.tk.metrics.domain

import net.playq.tk.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricPostgresMeterException, MacroMetricPostgresMeterTimeout, MacroMetricPostgresTimer}

import scala.language.experimental.macros

object MacroMetricsPostgres {
  object MeterTimeout extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"postgres/timeout-exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsPostgres.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsPostgres.discarded.type]
    override object CompileTime extends CompileTime
  }

  object MeterException extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"postgres/query-exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsPostgres.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsPostgres.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"postgres/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsPostgres.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsPostgres.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeterException[S <: String]: MacroMetricPostgresMeterException[S] = MeterException.empty[S]
    implicit def matMeterTimeout[S <: String]: MacroMetricPostgresMeterTimeout[S]     = MeterTimeout.empty[S]
    implicit def matTimer[S <: String]: MacroMetricPostgresTimer[S]                   = Timer.empty[S]
  }
}
