package d4s.query.predef

import d4s.conditions.Condition
import d4s.conditions.Condition.*
import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.*
import d4s.table.DynamoTableRef
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.*
import scala.util.chaining.scalaUtilChainingOps

final case class PutItem[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[PutItem, PutItemResponse],
  decoder: QueryDecoder[PutItemResponse, Dec],
  conditionExpression: Condition                                                   = ZeroCondition,
  attributeValues: Map[String, AttributeValue]                                     = Map.empty,
  attributeNames: Map[String, String]                                              = Map.empty,
  item: Map[String, AttributeValue]                                                = Map.empty,
  returnValue: Option[ReturnValue]                                                 = None,
  returnValuesOnConditionCheckFailure: Option[ReturnValuesOnConditionCheckFailure] = None,
) extends DynamoQuery.WithRequest[PutItem, Dec]
  with WithAttributeValues[PutItem[Dec]]
  with WithAttributeNames[PutItem[Dec]]
  with WithTableReference[PutItem, Dec]
  with WithCondition[PutItem[Dec]]
  with WithIfExistConditions[PutItem[Dec]]
  with WithCheckConditionSuccess[PutItem, Dec]
  with WithItem[PutItem[Dec]]
  with WithResponseAttributes[PutItem[Dec]]
  with WithResponseConsumed[PutItem, Dec]
  with WithReturnValue[PutItem[Dec]]
  with WithReturnValuesOnConditionCheckFailure[PutItem[Dec]] {

  override type Rq  = PutItemRequest
  override type Rsp = PutItemResponse

  override def withInterpreter(f: QueryInterpreter[PutItem, PutItemResponse]): PutItem[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[PutItemResponse, C1]): PutItem[C1] =
    copy(decoder = f)

  override def withItemAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): PutItem[Dec] =
    copy(item = f(item))

  override def withCondition(f: Condition => Condition): PutItem[Dec] =
    copy(conditionExpression = f(conditionExpression))

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): PutItem[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): PutItem[Dec] =
    copy(tableRef = f(tableRef))

  override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): PutItem[Dec] =
    copy(attributeValues = f(attributeValues))

  override def withReturnValue(newReturnValue: ReturnValue): PutItem[Dec] =
    copy(returnValue = Some(newReturnValue))

  override def withReturnValuesOnConditionCheckFailure(r: ReturnValuesOnConditionCheckFailure): PutItem[Dec] =
    copy(returnValuesOnConditionCheckFailure = Some(r))

  override def extractConsumedCapacity(rsp: PutItemResponse): ConsumedCapacity = {
    rsp.consumedCapacity()
  }

  override def extractAttributes(rsp: PutItemResponse): Map[String, AttributeValue] = {
    rsp.attributes().asScala.toMap
  }

  override def awsRequest: PutItemRequest = {
    val result = conditionExpression.eval().condition

    PutItemRequest
      .builder()
      .tableName(tableRef.tableName)
      .item(item.asJava)
      .conditionExpression(result.conditionExpression.orNull)
      .expressionAttributeValues(result.withAttributes(attributeValues))
      .expressionAttributeNames(result.withAliases(attributeNames))
      .pipe(b => returnValue.fold(b)(b.returnValues))
      .pipe(b => returnValuesOnConditionCheckFailure.fold(b)(b.returnValuesOnConditionCheckFailure))
      .build()
  }
}

object PutItem {
  def apply(table: DynamoTableRef[?]): PutItem[PutItemResponse] = {
    new PutItem(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[PutItem, PutItemResponse] = new QueryInterpreter[PutItem, PutItemResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: PutItem[Dec]): F[DynamoException, PutItemResponse] = {
      ctx.execute("PutItem", query.tableRef.tableName)(_.executeFuture(_.putItem(query.awsRequest)))
    }
  }
}
