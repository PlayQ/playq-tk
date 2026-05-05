package d4s.query.models

import d4s.codecs.D4SEncoder
import d4s.table.DynamoField
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.time.ZonedDateTime

trait WithItem[+D] extends WithTableReference.Generic[D] {
  self: D =>

  def withItemAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): D

  final def withItemAttributeValues(add: Map[String, AttributeValue]): D                   = withItemAttributeValues(_ ++ add)
  final def withItemAttributeValues(value: (String, AttributeValue)): D                    = withItemAttributeValues(_ + value)
  final def withItemField[T](field: DynamoField[T])(value: T): D                           = withItemAttributeValues(_ + field.bind(value))
  final def withItem[Item: D4SEncoder.AsObject](value: Item): D                            = withItemAttributeValues(D4SEncoder.AsObject[Item].encodeObject(value))
  final def withItems[I1: D4SEncoder.AsObject, I2: D4SEncoder.AsObject](v1: I1, v2: I2): D = withItemAttributeValues(
    D4SEncoder.AsObject[I1].encodeObject(v1) ++ D4SEncoder.AsObject[I2].encodeObject(v2)
  )

  final def withTtlFieldOption(expiration: Option[ZonedDateTime]): D = expiration.fold[D](self)(withTtlField)
  final def withTtlField(expiration: ZonedDateTime): D               = withTtlField(expiration.toEpochSecond)

  final def withTtlFieldOption(expirationEpochSeconds: Option[Long])(implicit dummy: DummyImplicit): D = {
    expirationEpochSeconds match {
      case Some(expiration) => withTtlField(expiration)
      case None             => self
    }
  }

  final def withTtlField(expirationEpochSeconds: Long): D = {
    tableRef.ttlField.fold[D](self) {
      t =>
        withItemField(DynamoField[Long](t))(expirationEpochSeconds)
    }
  }
}
