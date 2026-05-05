package d4s.query.predef

import d4s.UnsafeDynamoOps
import d4s.conditions.Condition.*
import d4s.conditions.{Condition, LogicalOperator}
import d4s.exceptions.DynamoException.InterpreterException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.*
import d4s.table.DynamoTableRef
import d4s.table.index.TableIndex
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

import java.util
import scala.jdk.CollectionConverters.{CollectionHasAsScala, MapHasAsScala}
import scala.util.chaining.*

/**
  * Query Request builder. Inherits WithPagesOffset, so that it can not be executed directly with DynamoConnector.
  * To execute this query you should complete it using one of the following methods (or it's flattenized variants):
  * - [[WithPages.PagedReader.readPage]]
  * - [[WithPages.PagedReader.readAll]]
  * - [[WithPages.PagedReader.readLimit]]
  * - [[WithPages.PagedReader.stream]]
  * - [[WithPagesOffset.OffsetPagedReader.readOffset]]
  * - [[WithPagesOffset.OffsetPagedReader.readOffsetLimit]]
  */
final case class Query[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[Query, QueryResponse],
  decoder: QueryDecoder[QueryResponse, Dec],
  index: Option[String]                                    = None,
  filterExpression: Condition                              = ZeroCondition,
  projectionExpression: Option[String]                     = None,
  pageSize: Option[Int]                                    = None,
  select: Option[Select]                                   = None,
  pageKey: Option[java.util.Map[String, AttributeValue]]   = None,
  consistentRead: Option[Boolean]                          = None,
  scanIndexForward: Option[Boolean]                        = None,
  attributeValues: Map[String, AttributeValue]             = Map.empty,
  attributeNames: Map[String, String]                      = Map.empty,
  conditionExpression: Condition                           = ZeroCondition,
  keyConditionAttributeValues: Map[String, AttributeValue] = Map.empty,
) extends DynamoQuery.WithRequest[Query, Dec]
  with WithTableReference[Query, Dec]
  with WithIndex[Query[Dec]]
  with WithKey[Query[Dec]]
  with WithAttributeValues[Query[Dec]]
  with WithAttributeNames[Query[Dec]]
  with WithFilterExpression[Query[Dec]]
  with WithCondition[Query[Dec]]
  with WithConsistent[Query[Dec]]
  with WithScanIndexForward[Query[Dec]]
  with WithPagesSize[Query[Dec]]
  with WithPages[Query, util.Map[String, AttributeValue], Dec]
  with WithPagesOffset[Query, util.Map[String, AttributeValue], QueryResponse, Dec]
  with WithResponseItems[Query, QueryResponse, Dec]
  with WithResponseConsumed[Query, Dec] {

  override type Rq  = QueryRequest
  override type Rsp = QueryResponse

  override def withInterpreter(f: QueryInterpreter[Query, QueryResponse]): Query[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[QueryResponse, C1]): Query[C1] =
    copy(decoder = f)

  override def withPageKey(pageKey: Option[util.Map[String, AttributeValue]]): Query[Dec] =
    copy(pageKey = pageKey.filter(!_.isEmpty))

  override def withCondition(f: Condition => Condition): Query[Dec] =
    copy(conditionExpression = f(conditionExpression))

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): Query[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withScanIndexForward(sif: Boolean): Query[Dec] =
    copy(scanIndexForward = Some(sif))

  override def withIndex(index: TableIndex[?, ?]): Query[Dec] =
    copy(index = Some(index.name))

  override def withSelect(newSelect: Select): Query[Dec] =
    copy(select = Some(newSelect))

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): Query[Dec] =
    copy(tableRef = f(tableRef))

  override def withProjectionExpression(f: Option[String] => Option[String]): Query[Dec] =
    copy(projectionExpression = f(projectionExpression))

  override def withFilterExpression(f: Condition => Condition): Query[Dec] =
    copy(filterExpression = f(filterExpression))

  override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): Query[Dec] =
    copy(attributeValues = f(attributeValues))

  override def withPageSize(l: Int)(implicit unsafe: UnsafeDynamoOps): Query[Dec] =
    copy(pageSize = Some(l))

  override def withConsistent(consistentRead: Boolean): Query[Dec] =
    copy(consistentRead = Some(consistentRead))

  override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): Query[Dec] =
    copy(keyConditionAttributeValues = f(keyConditionAttributeValues))

  override def extractItems(rsp: QueryResponse): List[Map[String, AttributeValue]] = {
    rsp.items().asScala.map(_.asScala.toMap).toList
  }

  override def extractPageKey(response: QueryResponse): Option[util.Map[String, AttributeValue]] = {
    Option(response.lastEvaluatedKey()).filter(!_.isEmpty)
  }

  override def extractItemsCount(response: QueryResponse): Int = {
    response.count()
  }

  override def extractScannedItemsCount(response: QueryResponse): Int = {
    response.scannedCount()
  }

  override def extractConsumedCapacity(rsp: QueryResponse): ConsumedCapacity = {
    rsp.consumedCapacity()
  }

  override def awsRequest: QueryRequest = {
    val evaluatedCondition = (conditionExpression :: keyConditionAttributeValues.toList.map { case (k, v) => logical(List(k), LogicalOperator.==, v) })
      .foldLeft[Condition](ZeroCondition)(and.apply).eval()

    val evaluatedFilter = filterExpression.eval(Some(evaluatedCondition)).condition

    QueryRequest
      .builder()
      .tableName(tableRef.tableName)
      .keyConditionExpression(evaluatedCondition.condition.conditionExpression.orNull)
      .filterExpression(evaluatedFilter.conditionExpression.orNull)
      .expressionAttributeValues(evaluatedCondition.condition.withAttributes(evaluatedFilter.attrValues ++ attributeValues))
      .expressionAttributeNames(evaluatedCondition.condition.withAliases(evaluatedFilter.aliases ++ attributeNames))
      .pipe(b => scanIndexForward.fold(b)(b.scanIndexForward(_)))
      .pipe(b => index.fold(b)(b.indexName))
      .pipe(b => projectionExpression.fold(b)(b.projectionExpression))
      .pipe(b => pageSize.fold(b)(b.limit(_)))
      .pipe(b => select.fold(b)(b.select))
      .pipe(b => pageKey.fold(b)(b.exclusiveStartKey))
      .pipe(b => consistentRead.fold(b)(b.consistentRead(_)))
      .build()
  }
}
object Query {
  def apply(table: DynamoTableRef[?]): Query[QueryResponse] = {
    new Query(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[Query, QueryResponse] = new QueryInterpreter[Query, QueryResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: Query[Dec]): F[InterpreterException, QueryResponse] = {
      ctx.execute("Query", query.tableRef.tableName)(_.executeFuture(_.query(query.awsRequest)))
    }
  }
}
