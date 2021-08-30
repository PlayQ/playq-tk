package net.playq.tk.aws.cost

import java.time.LocalDate

import software.amazon.awssdk.services.costexplorer.model.{Group, MetricValue, ResultByTime}

import scala.jdk.CollectionConverters.*

final case class CostResponse(
  groupDefinitions: Seq[CostGroupDefinition],
  resultsByTime: Seq[CostResultByTime],
  nextPageToken: String,
)

final case class CostResultByTime(
  estimated: Boolean,
  groups: Seq[CostGroup],
  from: LocalDate,
  to: LocalDate,
  total: Map[String, CostMetricValue],
)

object CostResultByTime {
  def apply(resultByTime: ResultByTime): CostResultByTime = {
    CostResultByTime(
      resultByTime.estimated(),
      resultByTime.groups().asScala.map(CostGroup(_)).toSeq,
      LocalDate.parse(resultByTime.timePeriod().start()),
      LocalDate.parse(resultByTime.timePeriod().end()),
      resultByTime.total().asScala.view.mapValues(CostMetricValue(_)).toMap,
    )
  }
}

final case class CostGroup(
  keys: Seq[String],
  metrics: Map[String, CostMetricValue],
)

object CostGroup {
  def apply(group: Group): CostGroup = {
    CostGroup(
      group.keys.asScala.toSeq,
      group.metrics.asScala.view.mapValues(CostMetricValue(_)).toMap,
    )
  }
}

final case class CostMetricValue(amount: String, unit: String)

object CostMetricValue {
  def apply(metricValue: MetricValue): CostMetricValue = {
    CostMetricValue(metricValue.amount, metricValue.unit)
  }
}
