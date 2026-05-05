package d4s.query.models

import d4s.codecs.D4SEncoder
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

trait WithAttributeValues[+D] {
  def withAttributeValues(f: Map[String, AttributeValue] => Map[String, AttributeValue]): D

  final def withAttributeValues(add: Map[String, AttributeValue]): D = {
    withAttributeValues(_ ++ add)
  }
  final def withAttributeValues(value: (String, AttributeValue)): D = {
    withAttributeValues(_ + value)
  }
  final def withAttribute[Item: D4SEncoder.AsObject](value: Item): D = {
    withAttributeValues(D4SEncoder.AsObject[Item].encodeObject(value))
  }
  final def withAttributes[I1: D4SEncoder.AsObject, I2: D4SEncoder.AsObject](v1: I1, v2: I2): D = {
    withAttributeValues(D4SEncoder.AsObject[I1].encodeObject(v1) ++ D4SEncoder.AsObject[I2].encodeObject(v2))
  }
}
