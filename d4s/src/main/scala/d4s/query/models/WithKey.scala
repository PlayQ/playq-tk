package d4s.query.models

import d4s.codecs.D4SEncoder
import d4s.table.DynamoField
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

trait WithKey[+D] {
  def withKey(f: Map[String, AttributeValue] => Map[String, AttributeValue]): D

  final def withKey(add: Map[String, AttributeValue]): D           = withKey(_ ++ add)
  final def withKey(addOne: (String, AttributeValue)): D           = withKey(_ + addOne)
  final def withKeyField[T](field: DynamoField[T])(value: T): D    = withKey(_ + field.bind(value))
  final def withKeyItem[Item: D4SEncoder.AsObject](value: Item): D = withKey(_ ++ D4SEncoder.AsObject[Item].encodeObject(value))
}
