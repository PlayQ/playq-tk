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

import java.util

final case class ScanDeleteBatch[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[ScanDeleteBatch, List[BatchWriteItemResponse]],
  decoder: QueryDecoder[List[BatchWriteItemResponse], Dec],
  maxParallelism: Option[Int]                            = None,
  index: Option[String]                                  = None,
  filterExpression: Condition                            = ZeroCondition,
  attributeValues: Map[String, AttributeValue]           = Map.empty,
  attributeNames: Map[String, String]                    = Map.empty,
  projectionExpression: Option[String]                   = None,
  pageSize: Option[Int]                                  = None,
  select: Option[Select]                                 = None,
  pageKey: Option[java.util.Map[String, AttributeValue]] = None,
  consistentRead: Boolean                                = false,
) extends DynamoQuery[ScanDeleteBatch, Dec]
  with WithFilterExpression[ScanDeleteBatch[Dec]]
  with WithAttributeValues[ScanDeleteBatch[Dec]]
  with WithAttributeNames[ScanDeleteBatch[Dec]]
  with WithProjectionExpression[ScanDeleteBatch[Dec]]
  with WithSelect[ScanDeleteBatch[Dec]]
  with WithPagesSize[ScanDeleteBatch[Dec]]
  with WithPagesKey[java.util.Map[String, AttributeValue], ScanDeleteBatch[Dec]]
  with WithTableReference[ScanDeleteBatch, Dec]
  with WithIndex[ScanDeleteBatch[Dec]]
  with WithConsistent[ScanDeleteBatch[Dec]]
  with WithParallelism[ScanDeleteBatch[Dec]] {

  override type Rsp = List[BatchWriteItemResponse]

  override def withInterpreter(f: QueryInterpreter[ScanDeleteBatch, List[BatchWriteItemResponse]]): ScanDeleteBatch[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[List[BatchWriteItemResponse], C1]): ScanDeleteBatch[C1] =
    copy(decoder = f)

  override def withParallelism(parallelism: Int): ScanDeleteBatch[Dec] =
    copy(maxParallelism = Some(parallelism))

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): ScanDeleteBatch[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withConsistent(consistentRead: Boolean): ScanDeleteBatch[Dec] =
    copy(consistentRead = consistentRead)

  override def withSelect(newSelect: Select): ScanDeleteBatch[Dec] =
    copy(select = Some(newSelect))

  override def withIndex(index: TableIndex[?, ?]): ScanDeleteBatch[Dec] =
    copy(index = Some(index.name))

  override def withPageSize(l: Int)(implicit unsafe: UnsafeDynamoOps): ScanDeleteBatch[Dec] =
    copy(pageSize = Some(l))

  override def withPageKey(pageKey: Option[util.Map[String, AttributeValue]]): ScanDeleteBatch[Dec] =
    copy(pageKey = pageKey)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): ScanDeleteBatch[Dec] =
    copy(tableRef = f(tableRef))

  override def withProjectionExpression(f: Option[String] => Option[String]): ScanDeleteBatch[Dec] =
    copy(projectionExpression = f(projectionExpression))

  override def withFilterExpression(f: Condition => Condition): ScanDeleteBatch[Dec] =
    copy(filterExpression = f(filterExpression))

  override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): ScanDeleteBatch[Dec] =
    copy(attributeValues = f(attributeValues))

  private def scan: Scan[ScanResponse] = Scan[ScanResponse](
    tableRef             = tableRef,
    interpreter          = Scan.interpreter,
    decoder              = QueryDecoder.pure,
    index                = index,
    filterExpression     = filterExpression,
    attributeValues      = attributeValues,
    attributeNames       = attributeNames,
    projectionExpression = projectionExpression,
    pageSize             = pageSize,
    select               = select,
    pageKey              = pageKey,
    consistentRead       = consistentRead,
  )
}

object ScanDeleteBatch {
  def apply(table: DynamoTableRef[?]): ScanDeleteBatch[List[BatchWriteItemResponse]] = {
    new ScanDeleteBatch(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[ScanDeleteBatch, List[BatchWriteItemResponse]] = new QueryInterpreter[ScanDeleteBatch, List[BatchWriteItemResponse]] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: ScanDeleteBatch[Dec]): F[DynamoException, List[BatchWriteItemResponse]] = {
      import ctx.concurrentThrowable
      val mainScan = query.scan
      mainScan
        .withProjectionExpression(mainScan.tableRef.key.keyFields.toList*)
        .decode(mainScan.extractItems)
        .stream.execute(ctx, QueryStreamLogger.empty)
        .chunkN(ctx.batchConfig.writeBatchSize)
        .covary[F[Throwable, _]]
        .parEvalMap(query.maxParallelism.getOrElse(Int.MaxValue)) {
          itemsChunk =>
            DeleteItemBatch(mainScan.tableRef)
              .withBatch(itemsChunk.toList)
              .interpret(ctx)
        }
        .flatMap(fs2.Stream.emits)
        .compile.toList.leftMap {
          case d: DynamoException => d
          case other              => InterpreterException("ScanDeleteBatch", List(mainScan.tableRef.tableName), other)
        }
    }
  }
}
