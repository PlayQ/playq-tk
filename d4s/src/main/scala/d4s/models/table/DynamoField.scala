package d4s.models.table

import d4s.codecs.{D4SAttributeEncoder, DynamoKeyAttribute}
import software.amazon.awssdk.services.dynamodb.model.{AttributeDefinition, AttributeValue, ScalarAttributeType}

final case class DynamoField[-T](name: String, attrType: ScalarAttributeType, encoder: T => AttributeValue) {
  override def toString: String = name

  def toAttribute: AttributeDefinition = {
    AttributeDefinition
      .builder()
      .attributeName(name)
      .attributeType(attrType)
      .build()
  }

  def bind(value: T): (String, AttributeValue) = name -> encoder(value)

  def contramap[B](f: B => T): DynamoField[B] = copy(encoder = encoder apply f(_))
}

object DynamoField {
  def apply[T](name: String)(implicit fieldAttribute: DynamoKeyAttribute[T], encoder: D4SAttributeEncoder[T]): DynamoField[T] = {
    DynamoField(name, fieldAttribute.attrType, encoder.encode)
  }
}
