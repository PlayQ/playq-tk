package d4s.query.models

import d4s.codecs.{D4SDecoder, ProjectionExpression}
import d4s.exceptions.DynamoException.DecoderException
import d4s.query.DynamoQuery
import d4s.query.effect.QueryDecoder
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.time.ZonedDateTime

trait WithResponseItem[DQ[+d] <: WithResponseItem[DQ, Rsp0, d], Rsp0, +Dec]
  extends DynamoQuery[DQ, Dec]
  with WithTableReference.Generic[DQ[Dec]]
  with WithProjectionExpression[DQ[Dec]] {
  self: DQ[Dec] =>

  override type Rsp = Rsp0

  def extractItem(rsp: Rsp): Map[String, AttributeValue]

  final def decodeItem[Item: D4SDecoder: ProjectionExpression]: DQ[Option[Item]] = {
    withProjectionExpression(ProjectionExpression[Item].projectionExpression)
      .decodeF(QueryDecoder.fromEither {
        rsp =>
          WithResponseItem.decodeItemImpl[Item](extractItem(rsp))
      })
  }

  final def decodeItemWithTTL[Item: D4SDecoder: ProjectionExpression]: DQ[Option[(Item, Option[Long])]] = {
    val ttlName = tableRef.ttlField.getOrElse(throw new RuntimeException(s"TTL field for table=${tableRef.tableName} not specified but requested."))
    withProjectionExpression(ProjectionExpression[Item].projectionExpression)
      .withProjectionExpression(ttlName)
      .decodeF(QueryDecoder.fromEither {
        rsp =>
          WithResponseItem.decodeItemTTLImpl[Item](ttlName)(extractItem(rsp))
      })
  }

  final def decodeItemCheckTTL[Item: D4SDecoder: ProjectionExpression](now: ZonedDateTime): DQ[Option[Item]] = {
    decodeItemCheckTTL(now.toEpochSecond)
  }

  final def decodeItemCheckTTL[Item: D4SDecoder: ProjectionExpression](nowEpochSeconds: Long): DQ[Option[Item]] = {
    decodeItemWithTTL.decodeWith {
      case (_, Some((item, ttl))) if ttl.forall(_ >= nowEpochSeconds) => Some(item)
      case (_, _)                                                     => None
    }
  }
}

object WithResponseItem {
  @inline def decodeItemImpl[Item: D4SDecoder](itemMap: Map[String, AttributeValue]): Either[DecoderException, Option[Item]] = {
    if (itemMap.nonEmpty) {
      D4SDecoder[Item].decodeObject(itemMap).map(Some(_))
    } else {
      Right(None)
    }
  }

  @inline def decodeItemTTLImpl[Item: D4SDecoder](
    ttlName: String
  )(itemMap: Map[String, AttributeValue]
  ): Either[DecoderException, Option[(Item, Option[Long])]] = {
    if (itemMap.nonEmpty) {
      for {
        item <- D4SDecoder[Item].decodeObject(itemMap)
        ttl   = itemMap.get(ttlName).flatMap(i => Option(i.n()).map(_.toLong))
      } yield Some((item, ttl))
    } else {
      Right(None)
    }
  }
}
