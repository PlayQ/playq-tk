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

final case class DeleteItem[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[DeleteItem, DeleteItemResponse],
  decoder: QueryDecoder[DeleteItemResponse, Dec],
  conditionExpression: Condition                                                   = ZeroCondition,
  attributeValues: Map[String, AttributeValue]                                     = Map.empty,
  attributeNames: Map[String, String]                                              = Map.empty,
  key: Map[String, AttributeValue]                                                 = Map.empty,
  returnValue: Option[ReturnValue]                                                 = None,
  returnValuesOnConditionCheckFailure: Option[ReturnValuesOnConditionCheckFailure] = None,
) extends DynamoQuery.WithRequest[DeleteItem, Dec]
  with WithTableReference[DeleteItem, Dec]
  with WithAttributeNames[DeleteItem[Dec]]
  with WithAttributeValues[DeleteItem[Dec]]
  with WithKey[DeleteItem[Dec]]
  with WithCondition[DeleteItem[Dec]]
  with WithIfExistConditions[DeleteItem[Dec]]
  with WithCheckConditionSuccess[DeleteItem, Dec]
  with WithResponseAttributes[DeleteItem[Dec]]
  with WithResponseConsumed[DeleteItem, Dec]
  with WithReturnValue[DeleteItem[Dec]]
  with WithReturnValuesOnConditionCheckFailure[DeleteItem[Dec]] {

  override type Rq  = DeleteItemRequest
  override type Rsp = DeleteItemResponse

  override def withInterpreter(f: QueryInterpreter[DeleteItem, DeleteItemResponse]): DeleteItem[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[DeleteItemResponse, C1]): DeleteItem[C1] =
    copy(decoder = f)

  override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): DeleteItem[Dec] =
    copy(key = f(key))

  override def withCondition(f: Condition => Condition): DeleteItem[Dec] =
    copy(conditionExpression = f(conditionExpression))

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): DeleteItem[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): DeleteItem[Dec] =
    copy(tableRef = f(tableRef))

  override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): DeleteItem[Dec] =
    copy(attributeValues = f(attributeValues))

  override def withReturnValue(newReturnValue: ReturnValue): DeleteItem[Dec] =
    copy(returnValue = Some(newReturnValue))

  override def withReturnValuesOnConditionCheckFailure(r: ReturnValuesOnConditionCheckFailure): DeleteItem[Dec] =
    copy(returnValuesOnConditionCheckFailure = Some(r))

  override def extractConsumedCapacity(rsp: DeleteItemResponse): ConsumedCapacity = {
    rsp.consumedCapacity()
  }

  override def extractAttributes(rsp: DeleteItemResponse): Map[String, AttributeValue] = {
    rsp.attributes().asScala.toMap
  }

  override def awsRequest: DeleteItemRequest = {
    val evaluatedCondition = conditionExpression.eval().condition

    DeleteItemRequest
      .builder()
      .tableName(tableRef.tableName)
      .key(key.asJava)
      .conditionExpression(evaluatedCondition.conditionExpression.orNull)
      .expressionAttributeValues(evaluatedCondition.withAttributes(attributeValues))
      .expressionAttributeNames(evaluatedCondition.withAliases(attributeNames))
      .pipe(b => returnValue.fold(b)(b.returnValues))
      .pipe(b => returnValuesOnConditionCheckFailure.fold(b)(b.returnValuesOnConditionCheckFailure))
      .build()
  }
}

object DeleteItem {
  def apply(table: DynamoTableRef[?]): DeleteItem[DeleteItemResponse] = {
    DeleteItem(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[DeleteItem, DeleteItemResponse] = new QueryInterpreter[DeleteItem, DeleteItemResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DeleteItem[Dec]): F[DynamoException, DeleteItemResponse] = {
      ctx.execute("DeleteItem", query.tableRef.tableName)(_.executeFuture(_.deleteItem(query.awsRequest)))
    }
  }
}
