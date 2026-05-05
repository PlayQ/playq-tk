package d4s.keys

import d4s.codecs.{D4SAttributeKeyType, D4SDecoder, D4SEncoder}
import d4s.util.{leftpadLongInvertNegative, reinvertNegative}

import java.util.UUID

/**
  * A string representation of UUID that preserves UUID and TimeUUID native ordering
  * for use as a RangeKey in Dynamo
  */
final class OrderedUUIDKey private (val asString: String) extends AnyVal

object OrderedUUIDKey {
  def apply(uuid: UUID): OrderedUUIDKey = {
    val mostSignificantString  = leftpadLongInvertNegative(uuid.getMostSignificantBits)
    val leastSignificantString = leftpadLongInvertNegative(uuid.getLeastSignificantBits)

    val uuidStr = s"$mostSignificantString:$leastSignificantString"

    new OrderedUUIDKey(uuidStr)
  }

  def parse(orderedUUIDKey: OrderedUUIDKey): UUID = {
    orderedUUIDKey.asString match {
      case s"$mst:$lst" =>
        val mostSignificantBits  = reinvertNegative(mst).toLong
        val leastSignificantBits = reinvertNegative(lst).toLong
        new UUID(mostSignificantBits, leastSignificantBits)
      case _ =>
        throw new IllegalArgumentException(s"Malformed OrderedUUIDKey `${orderedUUIDKey.asString}`")
    }
  }

  // encode as string
  implicit val encoder: D4SEncoder[OrderedUUIDKey] = D4SEncoder[String].contramap(_.asString)
  implicit val decoder: D4SDecoder[OrderedUUIDKey] = D4SDecoder[String].map(new OrderedUUIDKey(_))

  implicit val keyAttribute: D4SAttributeKeyType[OrderedUUIDKey] = D4SAttributeKeyType.S

  implicit val ordering: Ordering[OrderedUUIDKey] = Ordering[String].on(_.asString)
}
