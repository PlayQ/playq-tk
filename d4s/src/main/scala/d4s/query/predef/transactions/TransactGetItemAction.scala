package d4s.query.predef.transactions

import d4s.query.models.{WithAttributeNames, WithKey, WithProjectionExpression, WithTableReference}
import d4s.table.DynamoTableRef
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.*
import scala.util.chaining.scalaUtilChainingOps

trait TransactGetItemAction {
  def transactGetItem: TransactGetItem
}

object TransactGetItemAction {

  final case class TransactGet(
    tableRef: DynamoTableRef[?],
    key: Map[String, AttributeValue]     = Map.empty,
    attributeNames: Map[String, String]  = Map.empty,
    projectionExpression: Option[String] = None,
  ) extends TransactGetItemAction
    with WithTableReference.Generic[TransactGet]
    with WithKey[TransactGet]
    with WithAttributeNames[TransactGet]
    with WithProjectionExpression[TransactGet] {

    override def withTableReference(t: DynamoTableRef[?] => DynamoTableRef[?]): TransactGet =
      copy(tableRef = t(tableRef))

    override def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): TransactGet =
      copy(key = f(key))

    override def withAttributeNames(f: Map[String, String] => Map[String, String]): TransactGet =
      copy(attributeNames = f(attributeNames))

    override def withProjectionExpression(f: Option[String] => Option[String]): TransactGet =
      copy(projectionExpression = f(projectionExpression))

    def transactGetItem: TransactGetItem = {
      TransactGetItem
        .builder()
        .get(
          Get
            .builder()
            .tableName(tableRef.tableName)
            .key(key.asJava)
            .expressionAttributeNames(if (attributeNames.isEmpty) null else attributeNames.asJava)
            .pipe(b => projectionExpression.fold(b)(b.projectionExpression))
            .build()
        ).build()
    }
  }

  final case class Raw(override val transactGetItem: TransactGetItem) extends TransactGetItemAction
}
