package net.playq.tk

import net.playq.tk.metrics.domain.{MacroMetricsCost, MacroMetricsDynamo, MacroMetricsPostgres, MacroMetricsRedis, MacroMetricsRest, MacroMetricsS3, MacroMetricsSNS, MacroMetricsSQS, MacroMetricsSTS, MacroMetricsSagemaker}
import net.playq.tk.metrics.macrodefs.MacroMetricBase

package object metrics {
  final type MacroMetricTimer[S <: String]     = MacroMetricBase.Timer#MetricBase[S, Any]
  final type MacroMetricMeter[S <: String]     = MacroMetricBase.Meter#MetricBase[S, Any]
  final type MacroMetricCounter[S <: String]   = MacroMetricBase.Counter#MetricBase[S, Any]
  final type MacroMetricGauge[S <: String]     = MacroMetricBase.Gauge#MetricBase[S, Any]
  final type MacroMetricHistogram[S <: String] = MacroMetricBase.Histogram#MetricBase[S, Any]

  final type MacroMetricDynamoMeter[S <: String] = MacroMetricsDynamo.Meter.MetricBase[S, Any]
  final type MacroMetricDynamoTimer[S <: String] = MacroMetricsDynamo.Timer.MetricBase[S, Any]

  final type MacroMetricRestMeter[S <: String] = MacroMetricsRest.Meter.MetricBase[S, Any]
  final type MacroMetricRestTimer[S <: String] = MacroMetricsRest.Timer.MetricBase[S, Any]

  final type MacroMetricS3Meter[S <: String] = MacroMetricsS3.Meter.MetricBase[S, Any]
  final type MacroMetricS3Timer[S <: String] = MacroMetricsS3.Timer.MetricBase[S, Any]

  final type MacroMetricSQSMeter[S <: String] = MacroMetricsSQS.Meter.MetricBase[S, Any]
  final type MacroMetricSQSTimer[S <: String] = MacroMetricsSQS.Timer.MetricBase[S, Any]

  final type MacroMetricSNSMeter[S <: String] = MacroMetricsSNS.Meter.MetricBase[S, Any]
  final type MacroMetricSNSTimer[S <: String] = MacroMetricsSNS.Timer.MetricBase[S, Any]

  final type MacroMetricSTSMeter[S <: String] = MacroMetricsSTS.Meter.MetricBase[S, Any]
  final type MacroMetricSTSTimer[S <: String] = MacroMetricsSTS.Timer.MetricBase[S, Any]

  final type MacroMetricRedisMeter[S <: String] = MacroMetricsRedis.Meter.MetricBase[S, Any]
  final type MacroMetricRedisTimer[S <: String] = MacroMetricsRedis.Timer.MetricBase[S, Any]

  final type MacroMetricCostMeter[S <: String] = MacroMetricsCost.Meter.MetricBase[S, Any]
  final type MacroMetricCostTimer[S <: String] = MacroMetricsCost.Timer.MetricBase[S, Any]

  final type MacroMetricSagemakerMeter[S <: String] = MacroMetricsSagemaker.Meter.MetricBase[S, Any]
  final type MacroMetricSagemakerTimer[S <: String] = MacroMetricsSagemaker.Timer.MetricBase[S, Any]

  final type MacroMetricPostgresTimer[S <: String]          = MacroMetricsPostgres.Timer.MetricBase[S, Any]
  final type MacroMetricPostgresMeterTimeout[S <: String]   = MacroMetricsPostgres.MeterTimeout.MetricBase[S, Any]
  final type MacroMetricPostgresMeterException[S <: String] = MacroMetricsPostgres.MeterException.MetricBase[S, Any]
}
