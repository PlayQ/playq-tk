package d4s.query.predef

import d4s.query.DynamoQuery
import d4s.query.predef.batches.BatchGetRequestAction.IndividualBatchGetItem
import d4s.query.effect.{QueryDecoder, QueryInterpreter}
import d4s.query.models.*
import d4s.table.DynamoTableRef
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.*

final case class GetItemBatch[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[GetItemBatch, List[BatchGetItemResponse]],
  decoder: QueryDecoder[List[BatchGetItemResponse], Dec],
  batchItems: List[Map[String, AttributeValue]] = Nil,
  projectionExpression: Option[String]          = None,
  attributeNames: Map[String, String]           = Map.empty,
  consistentRead: Boolean                       = false,
) extends DynamoQuery[GetItemBatch, Dec]
  with WithTableReference[GetItemBatch, Dec]
  with WithResponseItems[GetItemBatch, List[BatchGetItemResponse], Dec]
  with WithBatch[GetItemBatch[Dec]]
  with WithAttributeNames[GetItemBatch[Dec]]
  with WithConsistent[GetItemBatch[Dec]] {

  override type Rsp = List[BatchGetItemResponse]

  override def withInterpreter(f: QueryInterpreter[GetItemBatch, List[BatchGetItemResponse]]): GetItemBatch[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[List[BatchGetItemResponse], C1]): GetItemBatch[C1] =
    copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): GetItemBatch[Dec] =
    copy(tableRef = f(tableRef))

  override def withProjectionExpression(f: Option[String] => Option[String]): GetItemBatch[Dec] =
    copy(projectionExpression = f(projectionExpression))

  override def withBatch(batchItems: List[Map[String, AttributeValue]]): GetItemBatch[Dec] =
    copy(batchItems = batchItems)

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): GetItemBatch[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withConsistent(consistentRead: Boolean): GetItemBatch[Dec] =
    copy(consistentRead = consistentRead)

  override def extractItems(rsp: List[BatchGetItemResponse]): List[Map[String, AttributeValue]] = {
    rsp.flatMap(_.responses().values().asScala.flatMap(_.asScala.map(_.asScala.toMap)))
  }

  private def requestBuilder: BatchGetItemRequest.Builder = BatchGetItemRequest.builder()

  private def requestItems: Map[String, List[IndividualBatchGetItem]] = {
    Map(tableRef.tableName -> batchItems.map(IndividualBatchGetItem(tableRef, _, attributeNames, projectionExpression, consistentRead)))
  }
}

object GetItemBatch {
  def apply(table: DynamoTableRef[?]): GetItemBatch[List[BatchGetItemResponse]] = {
    new GetItemBatch(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[GetItemBatch, List[BatchGetItemResponse]] = {
    BatchGet.executeGetBatch[GetItemBatch]("GetItemBatch")(
      _.requestBuilder,
      _.requestItems,
    )
  }
}
