package d4s.keys

import d4s.codecs.{D4SAttributeKeyType, D4SDecoder, D4SEncoder}
import d4s.util.{negateDigits, negateMinus, reinvertNegative}

import java.util.UUID
import scala.annotation.unused

/** An [[OrderedUUIDKey]] rendered with inverted digits and minus sign to reverse range key ordering in DynamoDB */
final class ReversedUUIDKey private (val asString: String) extends AnyVal

object ReversedUUIDKey {
  def apply(uuid: UUID): ReversedUUIDKey = {
    apply(OrderedUUIDKey(uuid))
  }

  def apply(digitUUID: OrderedUUIDKey)(implicit @unused d: DummyImplicit): ReversedUUIDKey = {
    new ReversedUUIDKey(negateMinus(negateDigits(digitUUID.asString)))
  }

  def parse(reversedUUIDKey: ReversedUUIDKey): UUID = {
    reversedUUIDKey.asString match {
      case s"$mst:$lst" =>
        val mostSignificantBits  = reinvertNegative(negateDigits(negateMinus(mst))).toLong
        val leastSignificantBits = reinvertNegative(negateDigits(negateMinus(lst))).toLong
        new UUID(mostSignificantBits, leastSignificantBits)
      case _ =>
        throw new IllegalArgumentException(s"Malformed ReversedUUIDKey `${reversedUUIDKey.asString}`")
    }
  }

  // encode as string
  implicit val encoder: D4SEncoder[ReversedUUIDKey] = D4SEncoder[String].contramap(_.asString)
  implicit val decoder: D4SDecoder[ReversedUUIDKey] = D4SDecoder[String].map(new ReversedUUIDKey(_))

  implicit val keyAttribute: D4SAttributeKeyType[ReversedUUIDKey] = D4SAttributeKeyType.S

  implicit val ordering: Ordering[ReversedUUIDKey] = Ordering[String].on(_.asString)
}
