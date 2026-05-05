package d4s.codecs.circe

import d4s.codecs.D4SEncoder
import io.circe.*
import io.circe.syntax.*
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import scala.jdk.CollectionConverters.*

object D4SCirceEncoder {
  def derived[T: Encoder]: D4SEncoder[T] = a => jsonToAttribute(a.asJson)

  object AsObject {
    def derived[T: Encoder.AsObject]: D4SEncoder.AsObject[T] = {
      _.asJsonObject.toMap.map { case (k, v) => k -> D4SCirceEncoder.jsonToAttribute(v) }
    }
  }

  def jsonToAttribute(json: Json): AttributeValue = {
    json.fold(
      jsonNull    = AttributeValue.builder().nul(true).build(),
      jsonBoolean = b => toAttr(b: java.lang.Boolean, _.bool),
      jsonNumber  = n => toAttr(n.toString, _.n),
      jsonString  = s => toAttr(s, _.s),
      jsonArray   = array => AttributeValue.builder.l(array.toList.map(jsonToAttribute).asJava).build(),
      jsonObject  = obj => toAttr(obj.toMap.map { case (k, v) => k -> jsonToAttribute(v) }.asJava, _.m),
    )
  }

  private[this] def toAttr[Z](t: Z, f: AttributeValue.Builder => Z => AttributeValue.Builder): AttributeValue = {
    f(AttributeValue.builder())(t).build()
  }
}
