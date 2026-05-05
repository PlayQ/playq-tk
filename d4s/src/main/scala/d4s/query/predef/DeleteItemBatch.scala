package d4s.query.predef

import d4s.query.DynamoQuery
import d4s.query.effect.{QueryDecoder, QueryInterpreter}
import d4s.query.models.{WithBatch, WithTableReference}
import d4s.table.DynamoTableRef
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.*

final case class DeleteItemBatch[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[DeleteItemBatch, List[BatchWriteItemResponse]],
  decoder: QueryDecoder[List[BatchWriteItemResponse], Dec],
  batchItems: List[Map[String, AttributeValue]] = Nil,
) extends DynamoQuery[DeleteItemBatch, Dec]
  with WithTableReference[DeleteItemBatch, Dec]
  with WithBatch[DeleteItemBatch[Dec]] {

  override type Rsp = List[BatchWriteItemResponse]

  override def withInterpreter(f: QueryInterpreter[DeleteItemBatch, List[BatchWriteItemResponse]]): DeleteItemBatch[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[List[BatchWriteItemResponse], C1]): DeleteItemBatch[C1] =
    copy(decoder = f)

  /** Overwrites batch list, subsequent calls do not append. */
  override def withBatch(batchItems: List[Map[String, AttributeValue]]): DeleteItemBatch[Dec] =
    copy(batchItems = batchItems)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): DeleteItemBatch[Dec] =
    copy(tableRef = f(tableRef))

  private def requestBuilder: BatchWriteItemRequest.Builder = {
    BatchWriteItemRequest.builder()
  }

  private def requestItems: Map[String, List[WriteRequest]] = {
    Map(tableRef.tableName -> batchItems.map(ll => WriteRequest.builder().deleteRequest(DeleteRequest.builder().key(ll.asJava).build()).build()))
  }
}

object DeleteItemBatch {
  def apply(table: DynamoTableRef[?]): DeleteItemBatch[List[BatchWriteItemResponse]] = {
    new DeleteItemBatch(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[DeleteItemBatch, List[BatchWriteItemResponse]] = {
    BatchWrite.executeWriteBatch[DeleteItemBatch]("DeleteItemBatch")(
      requestBuilder = _.requestBuilder,
      requestItems   = _.requestItems,
    )
  }
}
