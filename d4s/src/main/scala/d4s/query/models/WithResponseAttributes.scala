package d4s.query.models

import d4s.codecs.D4SDecoder
import d4s.query.DynamoRequest
import d4s.query.effect.QueryDecoder
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

trait WithResponseAttributes[+D <: DynamoRequest[?]] {
  self: D =>

  def extractAttributes(rsp: Rsp): Map[String, AttributeValue]

  final def decodeItem[Item: D4SDecoder]: self.SelfType[Option[Item]] = {
    decodeF(QueryDecoder.fromEither(rsp => WithResponseItem.decodeItemImpl(extractAttributes(rsp))))
  }
}
