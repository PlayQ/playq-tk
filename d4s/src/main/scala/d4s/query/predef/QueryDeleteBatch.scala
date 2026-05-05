package d4s.query.predef

import d4s.UnsafeDynamoOps
import d4s.conditions.Condition
import d4s.conditions.Condition.*
import d4s.exceptions.DynamoException
import d4s.exceptions.DynamoException.InterpreterException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter, QueryStreamLogger}
import d4s.query.models.*
import d4s.table.DynamoTableRef
import d4s.table.index.TableIndex
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

final case class QueryDeleteBatch[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[QueryDeleteBatch, List[BatchWriteItemResponse]],
  decoder: QueryDecoder[List[BatchWriteItemResponse], Dec],
  maxParallelism: Option[Int]                              = None,
  index: Option[String]                                    = None,
  filterExpression: Condition                              = ZeroCondition,
  projectionExpression: Option[String]                     = None,
  limit: Option[Int]                                       = None,
  select: Option[Select]                                   = None,
  pageKey: Option[java.util.Map[String, AttributeValue]]   = None,
  consistentRead: Option[Boolean]                          = None,
  scanIndexForward: Option[Boolean]                        = None,
  attributeValues: Map[String, AttributeValue]             = Map.empty,
  attributeNames: Map[String, String]                      = Map.empty,
  conditionExpression: Condition                           = ZeroCondition,
  keyConditionAttributeValues: Map[String, AttributeValue] = Map.empty,
) extends DynamoQuery[QueryDeleteBatch, Dec]
  with WithFilterExpression[QueryDeleteBatch[Dec]]
  with WithAttributeValues[QueryDeleteBatch[Dec]]
  with WithAttributeNames[QueryDeleteBatch[Dec]]
  with WithProjectionExpression[QueryDeleteBatch[Dec]]
  with WithSelect[QueryDeleteBatch[Dec]]
  with WithIndex[QueryDeleteBatch[Dec]]
  with WithPagesSize[QueryDeleteBatch[Dec]]
  with WithPagesKey[java.util.Map[String, AttributeValue], QueryDeleteBatch[Dec]]
  with WithConsistent[QueryDeleteBatch[Dec]]
  with WithScanIndexForward[QueryDeleteBatch[Dec]]
  with WithCondition[QueryDeleteBatch[Dec]]
  with WithTableReference[QueryDeleteBatch, Dec]
  with WithKey[QueryDeleteBatch[Dec]]
  with WithParallelism[QueryDeleteBatch[Dec]] {

  override type Rsp = List[BatchWriteItemResponse]

  override def withInterpreter(f: QueryInterpreter[QueryDeleteBatch, List[BatchWriteItemResponse]]): QueryDeleteBatch[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[List[BatchWriteItemResponse], C1]): QueryDeleteBatch[C1] =
    copy(decoder = f)

  override def withParallelism(parallelism: Int): QueryDeleteBatch[Dec] =
    copy(maxParallelism = Some(parallelism))

  override def withCondition(f: Condition => Condition): QueryDeleteBatch[Dec] =
    copy(conditionExpression = f(conditionExpression))

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): QueryDeleteBatch[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withScanIndexForward(sif: Boolean): QueryDeleteBatch[Dec] =
    copy(scanIndexForward = Some(sif))

  override def withIndex(index: TableIndex[?, ?]): QueryDeleteBatch[Dec] =
    copy(index = Some(index.name))

  override def withPageKey(pageKey: Option[java.util.Map[String, AttributeValue]]): QueryDeleteBatch[Dec] =
    copy(pageKey = pageKey)

  override def withSelect(newSelect: Select): QueryDeleteBatch[Dec] =
    copy(select = Some(newSelect))

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): QueryDeleteBatch[Dec] =
    copy(tableRef = f(tableRef))

  override def withProjectionExpression(f: Option[String] => Option[String]): QueryDeleteBatch[Dec] =
    copy(projectionExpression = f(projectionExpression))

  override def withFilterExpression(f: Condition => Condition): QueryDeleteBatch[Dec] =
    copy(filterExpression = f(filterExpression))

  override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): QueryDeleteBatch[Dec] =
    copy(attributeValues = f(attributeValues))

  override def withPageSize(l: Int)(implicit unsafe: UnsafeDynamoOps): QueryDeleteBatch[Dec] =
    copy(limit = Some(l))

  override def withConsistent(consistentRead: Boolean): QueryDeleteBatch[Dec] =
    copy(consistentRead = Some(consistentRead))

  override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): QueryDeleteBatch[Dec] =
    copy(keyConditionAttributeValues = f(keyConditionAttributeValues))

  private def query: Query[QueryResponse] = Query(
    tableRef                    = tableRef,
    interpreter                 = Query.interpreter,
    decoder                     = QueryDecoder.pure,
    index                       = index,
    filterExpression            = filterExpression,
    projectionExpression        = projectionExpression,
    pageSize                    = limit,
    select                      = select,
    pageKey                     = pageKey,
    consistentRead              = consistentRead,
    scanIndexForward            = scanIndexForward,
    attributeValues             = attributeValues,
    attributeNames              = attributeNames,
    conditionExpression         = conditionExpression,
    keyConditionAttributeValues = keyConditionAttributeValues,
  )

}

object QueryDeleteBatch {
  def apply(table: DynamoTableRef[?]): QueryDeleteBatch[List[BatchWriteItemResponse]] = {
    new QueryDeleteBatch(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[QueryDeleteBatch, List[BatchWriteItemResponse]] = new QueryInterpreter[QueryDeleteBatch, List[BatchWriteItemResponse]] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: QueryDeleteBatch[Dec]): F[DynamoException, List[BatchWriteItemResponse]] = {
      import ctx.concurrentThrowable
      val mainQuery = query.query
      mainQuery
        .withProjectionExpression(mainQuery.tableRef.key.keyFields.toList*)
        .decode(mainQuery.extractItems)
        .stream.execute(ctx, QueryStreamLogger.empty)
        .chunkN(ctx.batchConfig.writeBatchSize)
        .covary[F[Throwable, _]]
        .parEvalMap(query.maxParallelism.getOrElse(Int.MaxValue)) {
          itemsChunk =>
            DeleteItemBatch(mainQuery.tableRef)
              .withBatch(itemsChunk.toList)
              .interpret(ctx)
        }
        .flatMap(fs2.Stream.emits)
        .compile.toList.leftMap {
          case d: DynamoException => d
          case other              => InterpreterException("QueryDeleteBatch", List(mainQuery.tableRef.tableName), other)
        }
    }
  }
}
