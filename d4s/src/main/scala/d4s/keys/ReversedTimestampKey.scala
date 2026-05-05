package d4s.keys

import d4s.codecs.{D4SAttributeKeyType, D4SDecoder, D4SEncoder}
import d4s.util.negateDigits

import java.time.ZonedDateTime
import scala.annotation.unused

/**
  * An [[OrderedTimestampKey]] rendered with inverted digits to reverse range key ordering in DynamoDB
  *
  * @note Only 4 digit positive years are supported
  */
final class ReversedTimestampKey private (val asString: String) extends AnyVal

object ReversedTimestampKey {
  def apply(tsz: ZonedDateTime): ReversedTimestampKey = {
    apply(OrderedTimestampKey(tsz))
  }

  def apply(digitTimestamp: OrderedTimestampKey)(implicit @unused d: DummyImplicit): ReversedTimestampKey = {
    new ReversedTimestampKey(negateDigits(digitTimestamp.asString))
  }

  // encode as string
  implicit val encoder: D4SEncoder[ReversedTimestampKey] = D4SEncoder[String].contramap(_.asString)
  implicit val decoder: D4SDecoder[ReversedTimestampKey] = D4SDecoder[String].map(new ReversedTimestampKey(_))

  implicit val keyAttribute: D4SAttributeKeyType[ReversedTimestampKey] = D4SAttributeKeyType.S

  implicit val ordering: Ordering[ReversedTimestampKey] = Ordering[String].on(_.asString)
}
