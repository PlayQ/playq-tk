package d4s.query.predef.batches

import d4s.codecs.D4SEncoder
import d4s.query.DynamoRequest
import BatchGetRequestAction.IndividualBatchGetItem
import d4s.query.effect.QueryDecoder
import d4s.query.models.*
import d4s.table.DynamoTableRef
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, KeysAndAttributes}

import scala.jdk.CollectionConverters.*
import scala.util.chaining.scalaUtilChainingOps

trait BatchGetRequestAction[+Dec] {
  def individualGetItems(tableRef: DynamoTableRef[?]): List[IndividualBatchGetItem]
  def decoder: QueryDecoder[List[Map[String, AttributeValue]], Dec]
}

object BatchGetRequestAction {

  final case class BatchGetRequest[+Dec](
    decoder: QueryDecoder[List[Map[String, AttributeValue]], Dec],
    batchItems: List[Map[String, AttributeValue]] = Nil,
    projectionExpression: Option[String]          = None,
    attributeNames: Map[String, String]           = Map.empty,
    consistentRead: Boolean                       = false,
  ) extends BatchGetRequestAction[Dec]
    with DynamoRequest[Dec]
    with WithProjectionExpression[BatchGetRequest[Dec]]
    with WithResponseItems[BatchGetRequest, List[Map[String, AttributeValue]], Dec]
    with WithBatch[BatchGetRequest[Dec]]
    with WithAttributeNames[BatchGetRequest[Dec]]
    with WithConsistent[BatchGetRequest[Dec]] {

    override def extractItems(rsp: List[Map[String, AttributeValue]]): List[Map[String, AttributeValue]] = rsp

    override def decodeF[C1](f: QueryDecoder[List[Map[String, AttributeValue]], C1]): BatchGetRequest[C1] =
      copy(decoder = f)

    override def withProjectionExpression(f: Option[String] => Option[String]): BatchGetRequest[Dec] =
      copy(projectionExpression = f(projectionExpression))

    override def withBatch(batchItems: List[Map[String, AttributeValue]]): BatchGetRequest[Dec] =
      copy(batchItems = batchItems)

    override def withAttributeNames(f: Map[String, String] => Map[String, String]): BatchGetRequest[Dec] =
      copy(attributeNames = f(attributeNames))

    override def withConsistent(consistentRead: Boolean): BatchGetRequest[Dec] =
      copy(consistentRead = consistentRead)

    override def individualGetItems(tableRef: DynamoTableRef[?]): List[IndividualBatchGetItem] =
      batchItems.map(IndividualBatchGetItem(tableRef, _, attributeNames, projectionExpression, consistentRead))
  }
  object BatchGetRequest {
    def apply(): BatchGetRequest[List[Map[String, AttributeValue]]]                                                      = new BatchGetRequest(QueryDecoder.pure)
    def apply(keys: List[Map[String, AttributeValue]]): BatchGetRequest[List[Map[String, AttributeValue]]]               = new BatchGetRequest(QueryDecoder.pure, keys)
    def apply[Item: D4SEncoder.AsObject](batchItems: Iterable[Item]): BatchGetRequest[List[Map[String, AttributeValue]]] = {
      apply(batchItems.map(D4SEncoder.AsObject[Item].encodeObject).toList)
    }
  }

  final case class Raw[+Dec](
    individualBatchGetItems: DynamoTableRef[?] => List[IndividualBatchGetItem],
    override val decoder: QueryDecoder[List[Map[String, AttributeValue]], Dec],
  ) extends BatchGetRequestAction[Dec] {
    override def individualGetItems(tableRef: DynamoTableRef[?]): List[IndividualBatchGetItem] = individualBatchGetItems(tableRef)
  }

  final case class IndividualBatchGetItem(
    tableRef: DynamoTableRef[?],
    item: Map[String, AttributeValue],
    attributeNames: Map[String, String],
    projectionExpression: Option[String],
    consistentRead: Boolean,
  )
  object IndividualBatchGetItem {
    def toKeysAndAttributes(batchItems: List[IndividualBatchGetItem]): Map[String, KeysAndAttributes] = {
      batchItems.groupBy(_.tableRef).map {
        case (tableRef, items @ first :: _) =>
          tableRef.tableName ->
          KeysAndAttributes
            .builder()
            .keys(items.map(_.item.asJava).asJava)
            .expressionAttributeNames(if (first.attributeNames.isEmpty) null else first.attributeNames.asJava)
            .pipe(b => first.projectionExpression.fold(b)(b.projectionExpression))
            .consistentRead(first.consistentRead)
            .build()
        case _ => throw new RuntimeException("impossible")
      }
    }
  }

}
