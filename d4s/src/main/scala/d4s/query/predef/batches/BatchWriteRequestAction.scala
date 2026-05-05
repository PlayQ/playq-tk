package d4s.query.predef.batches

import d4s.codecs.D4SEncoder
import d4s.query.models.{WithItem, WithKey, WithTableReference}
import d4s.table.DynamoTableRef
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, DeleteRequest, PutRequest, WriteRequest}

import scala.jdk.CollectionConverters.*

trait BatchWriteRequestAction {
  def tableRef: DynamoTableRef[?]
  def writeRequest: WriteRequest
}

object BatchWriteRequestAction {

  final case class BatchPutRequest(
    tableRef: DynamoTableRef[?],
    item: Map[String, AttributeValue] = Map.empty,
  ) extends BatchWriteRequestAction
    with WithTableReference.Generic[BatchPutRequest]
    with WithItem[BatchPutRequest] {

    override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): BatchPutRequest =
      copy(tableRef = f(tableRef))

    override def withItemAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): BatchPutRequest =
      copy(item = f(item))

    override def writeRequest: WriteRequest = {
      WriteRequest.builder().putRequest(PutRequest.builder().item(item.asJava).build()).build()
    }
  }
  object BatchPutRequest {
    def apply[Item: D4SEncoder.AsObject](tableRef: DynamoTableRef[?], item: Item): BatchPutRequest =
      new BatchPutRequest(tableRef, D4SEncoder.AsObject[Item].encodeObject(item))
  }

  final case class BatchDeleteRequest(
    tableRef: DynamoTableRef[?],
    key: Map[String, AttributeValue] = Map.empty,
  ) extends BatchWriteRequestAction
    with WithTableReference.Generic[BatchDeleteRequest]
    with WithKey[BatchDeleteRequest] {

    override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): BatchDeleteRequest =
      copy(tableRef = f(tableRef))

    override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): BatchDeleteRequest =
      copy(key = f(key))

    override def writeRequest: WriteRequest = {
      WriteRequest.builder().deleteRequest(DeleteRequest.builder().key(key.asJava).build()).build()
    }
  }
  object BatchDeleteRequest {
    def apply[Key: D4SEncoder.AsObject](tableRef: DynamoTableRef[?], key: Key): BatchDeleteRequest =
      new BatchDeleteRequest(tableRef, D4SEncoder.AsObject[Key].encodeObject(key))
  }

  final case class Raw(override val tableRef: DynamoTableRef[?], override val writeRequest: WriteRequest) extends BatchWriteRequestAction

}
