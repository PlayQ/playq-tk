package net.playq.tk

import net.playq.tk.metrics.domain._

package object metrics {
  final type MacroMetricLegacyRestMeter[S <: String] = MacroMetricsLegacyRest.Meter.MetricBase[S, _]
  final type MacroMetricLegacyRestTimer[S <: String] = MacroMetricsLegacyRest.Timer.MetricBase[S, _]

  final type MacroMetricS3Meter[S <: String] = MacroMetricsS3.Meter.MetricBase[S, _]
  final type MacroMetricS3Timer[S <: String] = MacroMetricsS3.Timer.MetricBase[S, _]

  final type MacroMetricSQSMeter[S <: String] = MacroMetricsSQS.Meter.MetricBase[S, _]
  final type MacroMetricSQSTimer[S <: String] = MacroMetricsSQS.Timer.MetricBase[S, _]

  final type MacroMetricSTSMeter[S <: String] = MacroMetricsSTS.Meter.MetricBase[S, _]
  final type MacroMetricSTSTimer[S <: String] = MacroMetricsSTS.Timer.MetricBase[S, _]

  final type MacroMetricRedisMeter[S <: String] = MacroMetricsRedis.Meter.MetricBase[S, _]
  final type MacroMetricRedisTimer[S <: String] = MacroMetricsRedis.Timer.MetricBase[S, _]

  final type MacroMetricCostMeter[S <: String] = MacroMetricsCost.Meter.MetricBase[S, _]
  final type MacroMetricCostTimer[S <: String] = MacroMetricsCost.Timer.MetricBase[S, _]

  final type MacroMetricSagemakerMeter[S <: String] = MacroMetricsSagemaker.Meter.MetricBase[S, _]
  final type MacroMetricSagemakerTimer[S <: String] = MacroMetricsSagemaker.Timer.MetricBase[S, _]

  final type MacroMetricPostgresTimer[S <: String]          = MacroMetricsPostgres.Timer.MetricBase[S, _]
  final type MacroMetricPostgresMeterTimeout[S <: String]   = MacroMetricsPostgres.MeterTimeout.MetricBase[S, _]
  final type MacroMetricPostgresMeterException[S <: String] = MacroMetricsPostgres.MeterException.MetricBase[S, _]
}
