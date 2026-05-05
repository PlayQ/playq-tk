package d4s.keys

import d4s.codecs.{D4SAttributeKeyType, D4SDecoder, D4SEncoder}
import izumi.functional.bio.Clock1
import izumi.fundamentals.platform.time.IzTime

import java.time.{LocalDateTime, ZonedDateTime}

/**
  * A string representation of ZonedDateTime that preserves its native ordering
  * for use as a RangeKey in Dynamo
  *
  * @note Only 4 digit positive years are supported
  */
final class OrderedTimestampKey private (val asString: String) extends AnyVal

object OrderedTimestampKey {
  def apply(tsz: ZonedDateTime): OrderedTimestampKey = {
    apply(tsz.withZoneSameInstant(Clock1.TZ_UTC).toLocalDateTime)
  }

  def apply(utcDateTime: LocalDateTime): OrderedTimestampKey = {
    val format = IzTime.ISO_LOCAL_DATE_TIME_3NANO

    new OrderedTimestampKey(format.format(utcDateTime).filter(_.isDigit))
  }

  // encode as string
  implicit val encoder: D4SEncoder[OrderedTimestampKey] = D4SEncoder[String].contramap(_.asString)
  implicit val decoder: D4SDecoder[OrderedTimestampKey] = D4SDecoder[String].map(new OrderedTimestampKey(_))

  implicit val keyAttribute: D4SAttributeKeyType[OrderedTimestampKey] = D4SAttributeKeyType.S

  implicit val ordering: Ordering[OrderedTimestampKey] = Ordering[String].on(_.asString)
}
