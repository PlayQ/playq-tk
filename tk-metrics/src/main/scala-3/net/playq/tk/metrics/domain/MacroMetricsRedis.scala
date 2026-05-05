package net.playq.tk.metrics.domain

import net.playq.tk.metrics.macrodefs.MacroMetricBase
import net.playq.tk.metrics.{MacroMetricRedisMeter, MacroMetricRedisTimer}

import scala.language.experimental.macros

object MacroMetricsRedis {
  object Meter extends MacroMetricBase.Meter {
    override def createLabel(label: String): String                                            = s"redis/exception/$label"
    inline implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsRedis.discarded.type] = ${
      CompileTime.materializeImpl[S, this.type, MacroMetricsRedis.discarded.type]
    }
    override object CompileTime extends CompileTime
  }

  object Timer extends MacroMetricBase.Timer {
    override def createLabel(label: String): String                                            = s"redis/$label"
    inline implicit def matMeter[S <: String]: MetricBase[S, MacroMetricsRedis.discarded.type] = ${
      CompileTime.materializeImpl[S, this.type, MacroMetricsRedis.discarded.type]
    }
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matMeter[S <: String]: MacroMetricRedisMeter[S] = Meter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricRedisTimer[S] = Timer.empty[S]
  }
}
