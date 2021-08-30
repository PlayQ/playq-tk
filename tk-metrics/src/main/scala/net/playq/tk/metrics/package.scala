package net.playq.tk

import net.playq.tk.metrics.domain.{MacroMetricsCost, MacroMetricsDynamo, MacroMetricsPostgres, MacroMetricsRedis, MacroMetricsRest, MacroMetricsS3, MacroMetricsSQS, MacroMetricsSTS, MacroMetricsSagemaker}
import net.playq.tk.metrics.macrodefs.MacroMetricBase

package object metrics {
  final type MacroMetricTimer[S <: String]     = MacroMetricBase.Timer#MetricBase[S, ?]
  final type MacroMetricMeter[S <: String]     = MacroMetricBase.Meter#MetricBase[S, ?]
  final type MacroMetricCounter[S <: String]   = MacroMetricBase.Counter#MetricBase[S, ?]
  final type MacroMetricGauge[S <: String]     = MacroMetricBase.Gauge#MetricBase[S, ?]
  final type MacroMetricHistogram[S <: String] = MacroMetricBase.Histogram#MetricBase[S, ?]

  final type MacroMetricDynamoMeter[S <: String] = MacroMetricsDynamo.Meter.MetricBase[S, ?]
  final type MacroMetricDynamoTimer[S <: String] = MacroMetricsDynamo.Timer.MetricBase[S, ?]

  final type MacroMetricRestMeter[S <: String] = MacroMetricsRest.Meter.MetricBase[S, ?]
  final type MacroMetricRestTimer[S <: String] = MacroMetricsRest.Timer.MetricBase[S, ?]

  final type MacroMetricS3Meter[S <: String] = MacroMetricsS3.Meter.MetricBase[S, ?]
  final type MacroMetricS3Timer[S <: String] = MacroMetricsS3.Timer.MetricBase[S, ?]

  final type MacroMetricSQSMeter[S <: String] = MacroMetricsSQS.Meter.MetricBase[S, ?]
  final type MacroMetricSQSTimer[S <: String] = MacroMetricsSQS.Timer.MetricBase[S, ?]

  final type MacroMetricSTSMeter[S <: String] = MacroMetricsSTS.Meter.MetricBase[S, ?]
  final type MacroMetricSTSTimer[S <: String] = MacroMetricsSTS.Timer.MetricBase[S, ?]

  final type MacroMetricRedisMeter[S <: String] = MacroMetricsRedis.Meter.MetricBase[S, ?]
  final type MacroMetricRedisTimer[S <: String] = MacroMetricsRedis.Timer.MetricBase[S, ?]

  final type MacroMetricCostMeter[S <: String] = MacroMetricsCost.Meter.MetricBase[S, ?]
  final type MacroMetricCostTimer[S <: String] = MacroMetricsCost.Timer.MetricBase[S, ?]

  final type MacroMetricSagemakerMeter[S <: String] = MacroMetricsSagemaker.Meter.MetricBase[S, ?]
  final type MacroMetricSagemakerTimer[S <: String] = MacroMetricsSagemaker.Timer.MetricBase[S, ?]

  final type MacroMetricPostgresTimer[S <: String]          = MacroMetricsPostgres.Timer.MetricBase[S, ?]
  final type MacroMetricPostgresMeterTimeout[S <: String]   = MacroMetricsPostgres.MeterTimeout.MetricBase[S, ?]
  final type MacroMetricPostgresMeterException[S <: String] = MacroMetricsPostgres.MeterException.MetricBase[S, ?]
}
