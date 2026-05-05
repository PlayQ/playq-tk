package d4s.query.models

import cats.syntax.traverse.*
import d4s.codecs.{D4SDecoder, ProjectionExpression}
import d4s.query.DynamoRequest
import d4s.query.effect.QueryDecoder
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

trait WithResponseItems[DQ[+d] <: WithResponseItems[DQ, Rsp0, d], Rsp0, +Dec] extends DynamoRequest[Dec] with WithProjectionExpression[DQ[Dec]] {
  self: DQ[Dec] =>

  override type SelfType[+d] = DQ[d]

  override type Rsp = Rsp0

  def extractItems(rsp: Rsp): List[Map[String, AttributeValue]]

  final def decodeItems[Item: D4SDecoder: ProjectionExpression]: SelfType[List[Item]] = {
    withProjectionExpression(ProjectionExpression[Item].projectionExpression).decodeF(QueryDecoder.fromEither {
      rsp =>
        extractItems(rsp).traverse(WithResponseItem.decodeItemImpl[Item](_)).map(_.flatten)
    })
  }

  final def decodeItemsIgnoreErrors[Item: D4SDecoder: ProjectionExpression]: SelfType[List[Item]] = {
    withProjectionExpression(ProjectionExpression[Item].projectionExpression)
      .decodeF(QueryDecoder.syncThrowable {
        rsp =>
          extractItems(rsp).flatMap(WithResponseItem.decodeItemImpl[Item](_).toOption.flatten)
      })
  }

  def decodeItemsWithTTL[Item: D4SDecoder: ProjectionExpression](implicit ev: DQ[Dec] <:< WithTableReference.Generic[Any]): SelfType[List[(Item, Option[Long])]] = {
    val ttlName = ev(self).tableRef.ttlField.getOrElse(throw new RuntimeException(s"TTL field for table=${ev(self).tableRef.tableName} not specified but requested."))
    withProjectionExpression(ProjectionExpression[Item].projectionExpression)
      .withProjectionExpression(ttlName)
      .decodeF(QueryDecoder.fromEither {
        rsp =>
          extractItems(rsp).traverse(WithResponseItem.decodeItemTTLImpl[Item](ttlName)(_)).map(_.flatten)
      })
  }
}
