package net.playq.tk.aws.cost

import java.time.LocalDate

import software.amazon.awssdk.services.costexplorer.model.*

import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

final case class CostRequest private (
  from: LocalDate,
  to: LocalDate,
  metrics: Seq[String],
  granularity: String,
  groupBy: Option[Seq[CostGroupDefinition]],
  filter: Option[CostExpression],
  nextPageToken: Option[String],
) {

  def withGroupBy(groupBy: Seq[CostGroupDefinition]): CostRequest = {
    this.copy(groupBy = Some(groupBy))
  }

  def withFilter(filter: CostExpression): CostRequest = {
    this.copy(filter = Some(filter))
  }

  def withNextPageToken(token: Option[String]): CostRequest = {
    this.copy(nextPageToken = token)
  }

  def makeRequest(): GetCostAndUsageRequest = {
    GetCostAndUsageRequest
      .builder()
      .timePeriod(
        DateInterval
          .builder()
          .start(from.toString)
          .end(to.toString)
          .build()
      )
      .metrics(metrics.asJava)
      .granularity(granularity)
      .pipe(
        req =>
          groupBy.fold(req)(
            gr =>
              req.groupBy(
                gr.map(
                  definition =>
                    GroupDefinition
                      .builder()
                      .key(definition.key)
                      .`type`(definition.`type`)
                      .build()
                ).asJava
              )
          )
      )
      .pipe(req => filter.fold(req)(f => req.filter(f.toAmz())))
      .pipe(req => nextPageToken.fold(req)(req.nextPageToken))
      .build()

  }
}

object CostRequest {
  def apply(from: LocalDate, to: LocalDate, metrics: Seq[String], granularity: String): CostRequest = new CostRequest(from, to, metrics, granularity, None, None, None)
}

final case class CostGroupDefinition(`type`: String, key: String)

object CostGroupDefinition {
  def apply(groupDefinition: GroupDefinition): CostGroupDefinition = {
    CostGroupDefinition(groupDefinition.key(), groupDefinition.`type`().toString)
  }
}

final case class CostKeyValues(key: String, values: Seq[String])

final case class CostExpression(
  dimensions: Option[CostKeyValues],
  tags: Option[CostKeyValues],
  and: Option[Seq[CostExpression]],
  or: Option[Seq[CostExpression]],
  not: Option[CostExpression],
) {

  def withDimensions(keyValues: CostKeyValues): CostExpression = {
    this.copy(dimensions = Some(keyValues))
  }

  def withTags(keyValues: CostKeyValues): CostExpression = {
    this.copy(tags = Some(keyValues))
  }

  def withAnd(and: Seq[CostExpression]): CostExpression = {
    this.copy(and = Some(and))
  }

  def withOr(or: Seq[CostExpression]): CostExpression = {
    this.copy(or = Some(or))
  }

  def withNot(not: CostExpression): CostExpression = {
    this.copy(not = Some(not))
  }

  def toAmz(): Expression = {
    Expression
      .builder()
      .pipe(exp => dimensions.fold(exp)(d => exp.dimensions(DimensionValues.builder().key(d.key).values(d.values.asJava).build())))
      .pipe(exp => tags.fold(exp)(t => exp.tags(TagValues.builder().key(t.key).values(t.values.asJava).build())))
      .pipe(exp => and.fold(exp)(a => exp.and(a.map(_.toAmz()).asJava)))
      .pipe(exp => or.fold(exp)(o => exp.or(o.map(_.toAmz()).asJava)))
      .pipe(exp => not.fold(exp)(n => exp.not(n.toAmz())))
      .build()
  }
}

object CostExpression {
  def apply(): CostExpression = new CostExpression(None, None, None, None, None)
}
