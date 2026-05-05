package d4s.table

import d4s.codecs.{D4SAttributeKeyType, D4SEncoder}
import d4s.conditions.ConditionFieldSyntax.TypedPathFieldOps
import software.amazon.awssdk.services.dynamodb.model.{AttributeDefinition, AttributeValue, ScalarAttributeType}

import scala.language.implicitConversions

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
  def apply[T](name: String)(implicit fieldAttribute: D4SAttributeKeyType[T], encoder: D4SEncoder[T]): DynamoField[T] = {
    DynamoField(name, fieldAttribute.attrType, encoder.encode)
  }

  @inline implicit final def fieldOps[T](field: DynamoField[T]): TypedPathFieldOps[T] = new TypedPathFieldOps[T](List(field.name))
}
