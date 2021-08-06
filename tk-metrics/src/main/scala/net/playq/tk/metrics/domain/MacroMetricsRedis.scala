package net.playq.tk.metrics.domain

import net.playq.tk.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricRedisMeter, MacroMetricRedisTimer}

import scala.language.experimental.macros

object MacroMetricsRedis {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String = s"redis/exception/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsRedis.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsRedis.discarded.type]
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String = s"redis/$label"
    implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsRedis.discarded.type] = macro CompileTime.materializeImpl[S, MacroMetricsRedis.discarded.type]
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricRedisMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricRedisTimer[S] = Timer.empty[S]
  }
}
