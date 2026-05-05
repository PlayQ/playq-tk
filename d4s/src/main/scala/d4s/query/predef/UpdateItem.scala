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
import scala.util.chaining.*

final case class UpdateItem[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[UpdateItem, UpdateItemResponse],
  decoder: QueryDecoder[UpdateItemResponse, Dec],
  attributeValues: Map[String, AttributeValue]                                     = Map.empty,
  attributeNames: Map[String, String]                                              = Map.empty,
  updateExpressionItem: Map[String, AttributeValue]                                = Map.empty,
  updateExpression: String                                                         = "",
  conditionExpression: Condition                                                   = ZeroCondition,
  returnValue: Option[ReturnValue]                                                 = None,
  returnValuesOnConditionCheckFailure: Option[ReturnValuesOnConditionCheckFailure] = None,
) extends DynamoQuery.WithRequest[UpdateItem, Dec]
  with WithTableReference[UpdateItem, Dec]
  with WithAttributeValues[UpdateItem[Dec]]
  with WithAttributeNames[UpdateItem[Dec]]
  with WithItem[UpdateItem[Dec]]
  with WithUpdateExpression[UpdateItem[Dec]]
  with WithReturnValue[UpdateItem[Dec]]
  with WithCondition[UpdateItem[Dec]]
  with WithIfExistConditions[UpdateItem[Dec]]
  with WithCheckConditionSuccess[UpdateItem, Dec]
  with WithResponseAttributes[UpdateItem[Dec]]
  with WithResponseConsumed[UpdateItem, Dec]
  with WithReturnValuesOnConditionCheckFailure[UpdateItem[Dec]] {

  override type Rq  = UpdateItemRequest
  override type Rsp = UpdateItemResponse

  override def withInterpreter(f: QueryInterpreter[UpdateItem, UpdateItemResponse]): UpdateItem[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[UpdateItemResponse, C1]): UpdateItem[C1] =
    copy(decoder = f)

  override def withCondition(f: Condition => Condition): UpdateItem[Dec] =
    copy(conditionExpression = f(conditionExpression))

  override def withAttributeNames(f: Map[String, String] => Map[String, String]): UpdateItem[Dec] =
    copy(attributeNames = f(attributeNames))

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): UpdateItem[Dec] =
    copy(tableRef = f(tableRef))

  override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): UpdateItem[Dec] =
    copy(attributeValues = f(attributeValues))

  override def withUpdateExpression(f: String => String): UpdateItem[Dec] =
    copy(updateExpression = f(updateExpression))

  override def withItemAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): UpdateItem[Dec] =
    copy(updateExpressionItem = f(updateExpressionItem))

  override def withReturnValue(newReturnValue: ReturnValue): UpdateItem[Dec] =
    copy(returnValue = Some(newReturnValue))

  override def withReturnValuesOnConditionCheckFailure(r: ReturnValuesOnConditionCheckFailure): UpdateItem[Dec] =
    copy(returnValuesOnConditionCheckFailure = Some(r))

  override def extractConsumedCapacity(rsp: UpdateItemResponse): ConsumedCapacity = {
    rsp.consumedCapacity()
  }

  override def extractAttributes(rsp: UpdateItemResponse): Map[String, AttributeValue] = {
    rsp.attributes().asScala.toMap
  }

  override def awsRequest: UpdateItemRequest = {
    val (key, data)    = updateExpressionItem.partition { case (k, _) => tableRef.table.key.keyNames.contains(k) }
    val setExprs       = data.keySet.map(i => s"#$i = :$i").toList
    val setExprsString = setExprs.mkString(", ")

    val updateExprAttributeNames  = data.keySet.map(i => s"#$i" -> i).toMap ++ attributeNames
    val updateExprAttributeValues = data.map { case (k, v) => s":$k" -> v } ++ attributeValues

    val updateExpr = if (setExprs.isEmpty) {
      updateExpression
    } else {
      // there can only be one SET section in UpdateExpression
      updateExpression.split("SET ", 2).toList match {
        case List(beforeSET, "") =>
          beforeSET + s"SET $setExprsString"
        case List(beforeSET, afterSET) =>
          beforeSET + s"SET $setExprsString, $afterSET"
        case List(updateExpression) =>
          s"SET $setExprsString $updateExpression"
        case _ =>
          updateExpression
      }
    }
    val condExpr = conditionExpression.eval().condition

    UpdateItemRequest
      .builder()
      .tableName(tableRef.tableName)
      .updateExpression(updateExpr)
      .key(key.asJava)
      .conditionExpression(condExpr.conditionExpression.orNull)
      .expressionAttributeValues(condExpr.withAttributes(updateExprAttributeValues))
      .expressionAttributeNames(condExpr.withAliases(updateExprAttributeNames))
      .pipe(b => returnValue.fold(b)(b.returnValues))
      .pipe(b => returnValuesOnConditionCheckFailure.fold(b)(b.returnValuesOnConditionCheckFailure))
      .build()
  }
}

object UpdateItem {
  def apply(table: DynamoTableRef[?]): UpdateItem[UpdateItemResponse] = {
    new UpdateItem(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[UpdateItem, UpdateItemResponse] = new QueryInterpreter[UpdateItem, UpdateItemResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: UpdateItem[Dec]): F[DynamoException, UpdateItemResponse] = {
      val tableName = query.tableRef.tableName
      ctx.execute("UpdateItem", tableName)(_.executeFuture(_.updateItem(query.awsRequest)))
    }
  }

}
