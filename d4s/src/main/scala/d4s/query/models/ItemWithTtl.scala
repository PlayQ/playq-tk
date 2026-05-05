package d4s.query.models

import d4s.codecs.D4SEncoder
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

final case class ItemWithTtl[T](item: T, ttl: Option[Long] = None) {
  def encode(ttlName: String)(implicit encoder: D4SEncoder.AsObject[T]): Map[String, AttributeValue] = {
    ItemWithTtl.encodeWithTtl(ttlName, ttl)(item)
  }
}

object ItemWithTtl {
  @inline private[models] def encodeWithTtl[T: D4SEncoder.AsObject](ttlName: String, ttl: Option[Long])(item: T): Map[String, AttributeValue] = {
    val ttlAttributes = ttl.fold(Map.empty[String, AttributeValue])(D4SEncoder.encodeField(ttlName, _))
    D4SEncoder.AsObject[T].encodeObject(item) ++ ttlAttributes
  }
}
