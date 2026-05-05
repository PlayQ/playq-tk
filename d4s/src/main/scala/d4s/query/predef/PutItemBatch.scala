package d4s.query.predef

import d4s.query.DynamoQuery
import d4s.query.effect.{QueryDecoder, QueryInterpreter}
import d4s.query.models.{WithBatchTtl, WithTableReference}
import d4s.table.DynamoTableRef
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.*

final case class PutItemBatch[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[PutItemBatch, List[BatchWriteItemResponse]],
  decoder: QueryDecoder[List[BatchWriteItemResponse], Dec],
  batchItems: List[Map[String, AttributeValue]] = Nil,
) extends DynamoQuery[PutItemBatch, Dec]
  with WithTableReference[PutItemBatch, Dec]
  with WithBatchTtl[PutItemBatch[Dec]] {

  override type Rsp = List[BatchWriteItemResponse]

  override def withInterpreter(f: QueryInterpreter[PutItemBatch, List[BatchWriteItemResponse]]): PutItemBatch[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[List[BatchWriteItemResponse], C1]): PutItemBatch[C1] =
    copy(decoder = f)

  /** Overwrites batch list, subsequent calls do not append. */
  override def withBatch(batchItems: List[Map[String, AttributeValue]]): PutItemBatch[Dec] =
    copy(batchItems = batchItems)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): PutItemBatch[Dec] =
    copy(tableRef = f(tableRef))

  private def requestBuilder: BatchWriteItemRequest.Builder = {
    BatchWriteItemRequest.builder()
  }

  private def requestItems: Map[String, List[WriteRequest]] = {
    Map(tableRef.tableName -> batchItems.map(ll => WriteRequest.builder().putRequest(PutRequest.builder().item(ll.asJava).build()).build()))
  }
}

object PutItemBatch {
  def apply(table: DynamoTableRef[?]): PutItemBatch[List[BatchWriteItemResponse]] = {
    new PutItemBatch(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[PutItemBatch, List[BatchWriteItemResponse]] = {
    BatchWrite.executeWriteBatch[PutItemBatch]("PutItemBatch")(
      requestBuilder = _.requestBuilder,
      requestItems   = _.requestItems,
    )
  }
}
