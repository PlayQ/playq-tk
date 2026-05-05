package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.*
import d4s.table.DynamoTableRef
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, ConsumedCapacity, GetItemRequest, GetItemResponse}

import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

final case class GetItem[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[GetItem, GetItemResponse],
  decoder: QueryDecoder[GetItemResponse, Dec],
  projectionExpression: Option[String]         = None,
  attributeValues: Map[String, AttributeValue] = Map.empty,
  attributeNames: Map[String, String]          = Map.empty,
  key: Map[String, AttributeValue]             = Map.empty,
  consistentRead: Boolean                      = false,
) extends DynamoQuery.WithRequest[GetItem, Dec]
  with WithTableReference[GetItem, Dec]
  with WithAttributeNames[GetItem[Dec]]
  with WithKey[GetItem[Dec]]
  with WithConsistent[GetItem[Dec]]
  with WithResponseItem[GetItem, GetItemResponse, Dec]
  with WithResponseConsumed[GetItem, Dec] {

  override type Rq  = GetItemRequest
  override type Rsp = GetItemResponse

  override def withInterpreter(f: QueryInterpreter[GetItem, GetItemResponse]): GetItem[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[GetItemResponse, C1]): GetItem[C1] =
    copy(decoder = f)

  override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): GetItem[Dec] =
    copy(key = f(key))

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): GetItem[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): GetItem[Dec] =
    copy(tableRef = f(tableRef))

  override def withProjectionExpression(f: Option[String] => Option[String]): GetItem[Dec] =
    copy(projectionExpression = f(projectionExpression))

  override def withConsistent(consistentRead: Boolean): GetItem[Dec] =
    copy(consistentRead = consistentRead)

  override def extractItem(rsp: GetItemResponse): Map[String, AttributeValue] = {
    rsp.item().asScala.toMap
  }

  override def extractConsumedCapacity(rsp: GetItemResponse): ConsumedCapacity = {
    rsp.consumedCapacity()
  }

  override def awsRequest: GetItemRequest = {
    GetItemRequest
      .builder()
      .tableName(tableRef.tableName)
      .key(key.asJava)
      .expressionAttributeNames(if (attributeNames.isEmpty) null else attributeNames.asJava)
      .pipe(b => projectionExpression.fold(b)(b.projectionExpression))
      .consistentRead(consistentRead)
      .build()
  }
}

object GetItem {
  def apply(table: DynamoTableRef[?]): GetItem[GetItemResponse] = {
    new GetItem(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[GetItem, GetItemResponse] = new QueryInterpreter[GetItem, GetItemResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: GetItem[Dec]): F[DynamoException, GetItemResponse] = {
      ctx.execute("GetItem", query.tableRef.tableName)(_.executeFuture(_.getItem(query.awsRequest)))
    }
  }
}
