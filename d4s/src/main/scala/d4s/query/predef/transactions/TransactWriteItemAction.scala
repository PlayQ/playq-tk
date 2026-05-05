package d4s.query.predef.transactions

import d4s.conditions.Condition
import d4s.conditions.Condition.ZeroCondition
import d4s.query.models.{WithAttributeNames, WithAttributeValues, WithCondition, WithIfExistConditions, WithItem, WithKey, WithReturnValuesOnConditionCheckFailure, WithTableReference, WithUpdateExpression}
import d4s.table.DynamoTableRef
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.*
import scala.util.chaining.scalaUtilChainingOps

trait TransactWriteItemAction {
  def transactWriteItem: TransactWriteItem
}

object TransactWriteItemAction {

  final case class TransactPut(
    tableRef: DynamoTableRef[?],
    item: Map[String, AttributeValue]                                                = Map.empty,
    expressionAttributeValues: Map[String, AttributeValue]                           = Map.empty,
    expressionAttributeNames: Map[String, String]                                    = Map.empty,
    conditionExpression: Condition                                                   = ZeroCondition,
    returnValuesOnConditionCheckFailure: Option[ReturnValuesOnConditionCheckFailure] = None,
  ) extends TransactWriteItemAction
    with WithTableReference.Generic[TransactPut]
    with WithItem[TransactPut]
    with WithAttributeValues[TransactPut]
    with WithAttributeNames[TransactPut]
    with WithCondition[TransactPut]
    with WithIfExistConditions[TransactPut]
    with WithReturnValuesOnConditionCheckFailure[TransactPut] {

    override def withTableReference(t: DynamoTableRef[?] => DynamoTableRef[?]): TransactPut =
      copy(tableRef = t(tableRef))

    override def withItemAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactPut =
      copy(item = f(item))

    override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactPut =
      copy(expressionAttributeValues = f(expressionAttributeValues))

    override def withAttributeNames(f: Map[String, String] => Map[String, String]): TransactPut =
      copy(expressionAttributeNames = f(expressionAttributeNames))

    override def withCondition(f: Condition => Condition): TransactPut =
      copy(conditionExpression = f(conditionExpression))

    override def withReturnValuesOnConditionCheckFailure(r: ReturnValuesOnConditionCheckFailure): TransactPut =
      copy(returnValuesOnConditionCheckFailure = Some(r))

    def transactWriteItem: TransactWriteItem = {
      val evaluatedCondition = conditionExpression.eval().condition

      TransactWriteItem
        .builder()
        .put(
          Put
            .builder()
            .tableName(tableRef.tableName)
            .item(item.asJava)
            .conditionExpression(evaluatedCondition.conditionExpression.orNull)
            .expressionAttributeValues(evaluatedCondition.withAttributes(expressionAttributeValues))
            .expressionAttributeNames(evaluatedCondition.withAliases(expressionAttributeNames))
            .pipe(b => returnValuesOnConditionCheckFailure.fold(b)(b.returnValuesOnConditionCheckFailure))
            .build()
        ).build()
    }
  }

  final case class TransactDelete(
    tableRef: DynamoTableRef[?],
    key: Map[String, AttributeValue]                                                 = Map.empty,
    expressionAttributeValues: Map[String, AttributeValue]                           = Map.empty,
    expressionAttributeNames: Map[String, String]                                    = Map.empty,
    conditionExpression: Condition                                                   = ZeroCondition,
    returnValuesOnConditionCheckFailure: Option[ReturnValuesOnConditionCheckFailure] = None,
  ) extends TransactWriteItemAction
    with WithTableReference.Generic[TransactDelete]
    with WithKey[TransactDelete]
    with WithAttributeValues[TransactDelete]
    with WithAttributeNames[TransactDelete]
    with WithCondition[TransactDelete]
    with WithIfExistConditions[TransactDelete]
    with WithReturnValuesOnConditionCheckFailure[TransactDelete] {

    override def withTableReference(t: DynamoTableRef[?] => DynamoTableRef[?]): TransactDelete =
      copy(tableRef = t(tableRef))

    override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactDelete =
      copy(key = f(key))

    override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactDelete =
      copy(expressionAttributeValues = f(expressionAttributeValues))

    override def withAttributeNames(f: Map[String, String] => Map[String, String]): TransactDelete =
      copy(expressionAttributeNames = f(expressionAttributeNames))

    override def withCondition(f: Condition => Condition): TransactDelete =
      copy(conditionExpression = f(conditionExpression))

    override def withReturnValuesOnConditionCheckFailure(r: ReturnValuesOnConditionCheckFailure): TransactDelete =
      copy(returnValuesOnConditionCheckFailure = Some(r))

    def transactWriteItem: TransactWriteItem = {
      val evaluatedCondition = conditionExpression.eval().condition

      TransactWriteItem
        .builder()
        .delete(
          Delete
            .builder()
            .tableName(tableRef.tableName)
            .key(key.asJava)
            .conditionExpression(evaluatedCondition.conditionExpression.orNull)
            .expressionAttributeValues(evaluatedCondition.withAttributes(expressionAttributeValues))
            .expressionAttributeNames(evaluatedCondition.withAliases(expressionAttributeNames))
            .pipe(b => returnValuesOnConditionCheckFailure.fold(b)(b.returnValuesOnConditionCheckFailure))
            .build()
        ).build()
    }
  }

  final case class TransactUpdate(
    tableRef: DynamoTableRef[?],
    key: Map[String, AttributeValue]                                                 = Map.empty,
    updateExpression: String                                                         = "",
    expressionAttributeValues: Map[String, AttributeValue]                           = Map.empty,
    expressionAttributeNames: Map[String, String]                                    = Map.empty,
    conditionExpression: Condition                                                   = ZeroCondition,
    returnValuesOnConditionCheckFailure: Option[ReturnValuesOnConditionCheckFailure] = None,
  ) extends TransactWriteItemAction
    with WithTableReference.Generic[TransactUpdate]
    with WithKey[TransactUpdate]
    with WithUpdateExpression[TransactUpdate]
    with WithAttributeValues[TransactUpdate]
    with WithAttributeNames[TransactUpdate]
    with WithCondition[TransactUpdate]
    with WithIfExistConditions[TransactUpdate]
    with WithReturnValuesOnConditionCheckFailure[TransactUpdate] {

    override def withTableReference(t: DynamoTableRef[?] => DynamoTableRef[?]): TransactUpdate =
      copy(tableRef = t(tableRef))

    override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactUpdate =
      copy(key = f(key))

    override def withUpdateExpression(f: String => String): TransactUpdate =
      copy(updateExpression = f(updateExpression))

    override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactUpdate =
      copy(expressionAttributeValues = f(expressionAttributeValues))

    override def withAttributeNames(f: Map[String, String] => Map[String, String]): TransactUpdate =
      copy(expressionAttributeNames = f(expressionAttributeNames))

    override def withCondition(f: Condition => Condition): TransactUpdate =
      copy(conditionExpression = f(conditionExpression))

    override def withReturnValuesOnConditionCheckFailure(r: ReturnValuesOnConditionCheckFailure): TransactUpdate =
      copy(returnValuesOnConditionCheckFailure = Some(r))

    def transactWriteItem: TransactWriteItem = {
      val evaluatedCondition = conditionExpression.eval().condition

      TransactWriteItem
        .builder()
        .update(
          Update
            .builder()
            .tableName(tableRef.tableName)
            .key(key.asJava)
            .updateExpression(updateExpression)
            .conditionExpression(evaluatedCondition.conditionExpression.orNull)
            .expressionAttributeValues(evaluatedCondition.withAttributes(expressionAttributeValues))
            .expressionAttributeNames(evaluatedCondition.withAliases(expressionAttributeNames))
            .pipe(b => returnValuesOnConditionCheckFailure.fold(b)(b.returnValuesOnConditionCheckFailure))
            .build()
        ).build()
    }
  }

  final case class TransactConditionCheck(
    tableRef: DynamoTableRef[?],
    key: Map[String, AttributeValue]                                                 = Map.empty,
    expressionAttributeValues: Map[String, AttributeValue]                           = Map.empty,
    expressionAttributeNames: Map[String, String]                                    = Map.empty,
    conditionExpression: Condition                                                   = ZeroCondition,
    returnValuesOnConditionCheckFailure: Option[ReturnValuesOnConditionCheckFailure] = None,
  ) extends TransactWriteItemAction
    with WithTableReference.Generic[TransactConditionCheck]
    with WithKey[TransactConditionCheck]
    with WithAttributeValues[TransactConditionCheck]
    with WithAttributeNames[TransactConditionCheck]
    with WithCondition[TransactConditionCheck]
    with WithReturnValuesOnConditionCheckFailure[TransactConditionCheck] {

    override def withTableReference(t: DynamoTableRef[?] => DynamoTableRef[?]): TransactConditionCheck =
      copy(tableRef = t(tableRef))

    override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactConditionCheck =
      copy(key = f(key))

    override def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactConditionCheck =
      copy(expressionAttributeValues = f(expressionAttributeValues))

    override def withAttributeNames(f: Map[String, String] => Map[String, String]): TransactConditionCheck =
      copy(expressionAttributeNames = f(expressionAttributeNames))

    override def withCondition(f: Condition => Condition): TransactConditionCheck =
      copy(conditionExpression = f(conditionExpression))

    override def withReturnValuesOnConditionCheckFailure(r: ReturnValuesOnConditionCheckFailure): TransactConditionCheck =
      copy(returnValuesOnConditionCheckFailure = Some(r))

    def transactWriteItem: TransactWriteItem = {
      val evaluatedCondition = conditionExpression.eval().condition

      TransactWriteItem
        .builder()
        .conditionCheck(
          ConditionCheck
            .builder()
            .tableName(tableRef.tableName)
            .key(key.asJava)
            .conditionExpression(evaluatedCondition.conditionExpression.orNull)
            .expressionAttributeValues(evaluatedCondition.withAttributes(expressionAttributeValues))
            .expressionAttributeNames(evaluatedCondition.withAliases(expressionAttributeNames))
            .pipe(b => returnValuesOnConditionCheckFailure.fold(b)(b.returnValuesOnConditionCheckFailure))
            .build()
        ).build()
    }
  }

  final case class Raw(override val transactWriteItem: TransactWriteItem) extends TransactWriteItemAction
}
