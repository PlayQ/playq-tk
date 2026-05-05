package d4s.query.predef

import d4s.UnsafeDynamoOps
import d4s.conditions.Condition
import d4s.conditions.Condition.*
import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.*
import d4s.table.DynamoTableRef
import d4s.table.index.TableIndex
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

import java.util
import scala.jdk.CollectionConverters.{IterableHasAsScala, MapHasAsScala}
import scala.util.chaining.*

/**
  * Scan Request builder.
  * Inherits WithPagesOffset, so that it can not be executed directly with DynamoConnector.
  * To execute this query you should complete it using one of the following methods (or it's flattenized variants):
  * - [[WithPages.PagedReader.readPage]]
  * - [[WithPages.PagedReader.readAll]]
  * - [[WithPages.PagedReader.readLimit]]
  * - [[WithPages.PagedReader.stream]]
  * - [[WithPagesOffset.OffsetPagedReader.readOffset]]
  * - [[WithPagesOffset.OffsetPagedReader.readOffsetLimit]]
  */
final case class Scan[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[Scan, ScanResponse],
  decoder: QueryDecoder[ScanResponse, Dec],
  index: Option[String]                                  = None,
  filterExpression: Condition                            = ZeroCondition,
  attributeValues: Map[String, AttributeValue]           = Map.empty,
  attributeNames: Map[String, String]                    = Map.empty,
  projectionExpression: Option[String]                   = None,
  pageSize: Option[Int]                                  = None,
  select: Option[Select]                                 = None,
  pageKey: Option[java.util.Map[String, AttributeValue]] = None,
  consistentRead: Boolean                                = false,
) extends DynamoQuery.WithRequest[Scan, Dec]
  with WithTableReference[Scan, Dec]
  with WithIndex[Scan[Dec]]
  with WithAttributeNames[Scan[Dec]]
  with WithAttributeValues[Scan[Dec]]
  with WithProjectionExpression[Scan[Dec]]
  with WithSelect[Scan[Dec]]
  with WithFilterExpression[Scan[Dec]]
  with WithConsistent[Scan[Dec]]
  with WithPagesSize[Scan[Dec]]
  with WithPages[Scan, util.Map[String, AttributeValue], Dec]
  with WithPagesOffset[Scan, util.Map[String, AttributeValue], ScanResponse, Dec]
  with WithResponseItems[Scan, ScanResponse, Dec]
  with WithResponseConsumed[Scan, Dec] {

  override type Rq  = ScanRequest
  override type Rsp = ScanResponse

  override def withInterpreter(f: QueryInterpreter[Scan, ScanResponse]): Scan[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[ScanResponse, C1]): Scan[C1] =
    copy(decoder = f)

  override def withPageKey(pageKey: Option[util.Map[String, AttributeValue]]): Scan[Dec] =
    copy(pageKey = pageKey.filter(!_.isEmpty))

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): Scan[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withConsistent(consistentRead: Boolean): Scan[Dec] =
    copy(consistentRead = consistentRead)

  override def withSelect(newSelect: Select): Scan[Dec] =
    copy(select = Some(newSelect))

  override def withIndex(index: TableIndex[?, ?]): Scan[Dec] =
    copy(index = Some(index.name))

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): Scan[Dec] =
    copy(tableRef = f(tableRef))

  override def withProjectionExpression(f: Option[String] => Option[String]): Scan[Dec] =
    copy(projectionExpression = f(projectionExpression))

  override def withFilterExpression(f: Condition => Condition): Scan[Dec] =
    copy(filterExpression = f(filterExpression))

  override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): Scan[Dec] =
    copy(attributeValues = f(attributeValues))

  override def withPageSize(l: Int)(implicit unsafe: UnsafeDynamoOps): Scan[Dec] =
    copy(pageSize = Some(l))

  override def extractItems(rsp: ScanResponse): List[Map[String, AttributeValue]] = {
    rsp.items().asScala.map(_.asScala.toMap).toList
  }

  override def extractPageKey(rsp: ScanResponse): Option[util.Map[String, AttributeValue]] = {
    Option(rsp.lastEvaluatedKey()).filter(!_.isEmpty)
  }

  override def extractItemsCount(rsp: ScanResponse): Int = {
    rsp.count()
  }

  override def extractScannedItemsCount(rsp: ScanResponse): Int = {
    rsp.scannedCount()
  }

  override def extractConsumedCapacity(rsp: ScanResponse): ConsumedCapacity = {
    rsp.consumedCapacity()
  }

  override def awsRequest: ScanRequest = {
    val evaluatedFilter = filterExpression.eval().condition

    ScanRequest
      .builder()
      .tableName(tableRef.tableName)
      .expressionAttributeValues(evaluatedFilter.withAttributes(attributeValues))
      .expressionAttributeNames(evaluatedFilter.withAliases(attributeNames))
      .filterExpression(evaluatedFilter.conditionExpression.orNull)
      .pipe(b => index.fold(b)(b.indexName))
      .pipe(b => projectionExpression.fold(b)(b.projectionExpression))
      .pipe(b => pageSize.fold(b)(b.limit(_)))
      .pipe(b => select.fold(b)(b.select))
      .pipe(b => pageKey.fold(b)(b.exclusiveStartKey))
      .consistentRead(consistentRead)
      .build()
  }
}

object Scan {
  def apply(table: DynamoTableRef[?]): Scan[ScanResponse] = {
    new Scan(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[Scan, ScanResponse] = new QueryInterpreter[Scan, ScanResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: Scan[Dec]): F[DynamoException, ScanResponse] = {
      ctx.execute("Scan", query.tableRef.tableName)(_.executeFuture(_.scan(query.awsRequest)))
    }
  }
}
