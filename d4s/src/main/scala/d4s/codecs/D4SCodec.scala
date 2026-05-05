package d4s.codecs

import d4s.codecs.D4SCodec.AsObject.Imap2PartiallyApplied
import d4s.codecs.D4SCodec.fromPair
import d4s.codecs.WithD4SMacro.{D4SDerivedCodec, D4SDerivedCodecAsObject}
import d4s.exceptions.DynamoException.DecoderException
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import scala.reflect.{ClassTag, classTag}

trait D4SCodec[A] extends D4SEncoder[A] with D4SDecoder[A] {
  def imap[B](decode: A => B)(encode: B => A): D4SCodec[B] = fromPair(contramap(encode), map(decode))
}

object D4SCodec {
  @inline def apply[A](implicit ev: D4SCodec[A]): ev.type = ev

  def derived[A](implicit derivedCodec: D4SDerivedCodec[A]): D4SCodec[A] = D4SCodec.fromPair(derivedCodec.enc, derivedCodec.dec)

  def forAnyVal[A, Underlying: D4SEncoder: D4SDecoder](construct: Underlying => A)(extract: A => Underlying): D4SCodec[A] = {
    D4SCodec.fromPair(D4SEncoder[Underlying].contramap(extract), D4SDecoder[Underlying].map(construct))
  }

  def from[T: D4SEncoder: D4SDecoder]: D4SCodec[T]                             = fromPair(D4SEncoder[T], D4SDecoder[T])
  def fromPair[T](encoder: D4SEncoder[T], decoder: D4SDecoder[T]): D4SCodec[T] = new D4SCodec[T] {
    override def encode(item: T): AttributeValue = encoder.encode(item)

    override def decodeObject(item: Map[String, AttributeValue]): Either[DecoderException, T] = decoder.decodeObject(item)
    override def decode(attr: AttributeValue): Either[DecoderException, T]                    = decoder.decode(attr)
  }

  trait AsObject[A] extends D4SCodec[A] with D4SEncoder.AsObject[A] {
    override final def imap[B](decode: A => B)(encode: B => A): D4SCodec.AsObject[B] =
      D4SCodec.AsObject.fromPair(contramap(encode), map(decode))

    final def imap2[B, C](another: D4SCodec.AsObject[B])(decode: (A, B) => C)(encode: C => (A, B)): D4SCodec.AsObject[C] =
      D4SCodec.AsObject.fromPair(contramap2(another)(encode), map2(another)(decode))
    final def imap2[B, C](anotherEnc: D4SEncoder.AsObject[B], anotherDec: D4SDecoder[B])(decode: (A, B) => C)(encode: C => (A, B)): D4SCodec.AsObject[C] =
      D4SCodec.AsObject.fromPair(contramap2(anotherEnc)(encode), map2(anotherDec)(decode))
    final def imap2[B]: Imap2PartiallyApplied[A, B] = new Imap2PartiallyApplied[A, B](this)

    final def imapObject(
      decode: Map[String, AttributeValue] => Map[String, AttributeValue]
    )(encode: Map[String, AttributeValue] => Map[String, AttributeValue]
    ): D4SCodec.AsObject[A] =
      D4SCodec.AsObject.fromPair(postprocessObjectEncoder(decode), preprocessObjectDecoder(encode))

    override final def appendFields[Item: D4SEncoder.AsObject](f: (A, Map[String, AttributeValue]) => Item): D4SCodec.AsObject[A] =
      D4SCodec.AsObject.fromPair(super.appendFields(f), this)
  }
  object AsObject {
    @inline def apply[A](implicit ev: D4SCodec.AsObject[A]): ev.type = ev

    def derived[A](implicit derivedCodec: D4SDerivedCodecAsObject[A]): D4SCodec.AsObject[A] = D4SCodec.AsObject.fromPair(derivedCodec.enc, derivedCodec.dec)

    def singleField[T, Field: D4SEncoder: D4SDecoder: ClassTag](fieldName: String)(construct: Field => T)(extract: T => Field): D4SCodec.AsObject[T] = {
      val objectEncoder = new D4SEncoder.AsObject[T] {
        override def encodeObject(item: T): Map[String, AttributeValue] = {
          Map(fieldName -> D4SEncoder[Field].encode(extract(item)))
        }
      }
      val objectDecoder = D4SDecoder.objectDecoder {
        item =>
          D4SDecoder[Field].decodeOptional(item.get(fieldName), fieldName, classTag[Field].runtimeClass.getSimpleName).map(construct)
      }
      D4SCodec.AsObject.fromPair(objectEncoder, objectDecoder)
    }

    def forAnyVal[A, Underlying: D4SEncoder.AsObject: D4SDecoder](construct: Underlying => A)(extract: A => Underlying): D4SCodec.AsObject[A] = {
      D4SCodec.AsObject.fromPair(D4SEncoder.AsObject[Underlying].contramap(extract), D4SDecoder[Underlying].map(construct))
    }

    def from[T: D4SEncoder.AsObject: D4SDecoder]: D4SCodec.AsObject[T]                             = fromPair(D4SEncoder.AsObject[T], D4SDecoder[T])
    def fromPair[T](encoder: D4SEncoder.AsObject[T], decoder: D4SDecoder[T]): D4SCodec.AsObject[T] = new D4SCodec.AsObject[T] {
      override def encodeObject(item: T): Map[String, AttributeValue]                           = encoder.encodeObject(item)
      override def decodeObject(item: Map[String, AttributeValue]): Either[DecoderException, T] = decoder.decodeObject(item)
      override def decode(attr: AttributeValue): Either[DecoderException, T]                    = decoder.decode(attr)
    }

    final class Imap2PartiallyApplied[A, B](private val self: D4SCodec.AsObject[A]) extends AnyVal {
      def apply[C](decode: (A, B) => C)(encode: C => (A, B))(implicit enc: D4SEncoder.AsObject[B], dec: D4SDecoder[B]): D4SCodec.AsObject[C] = {
        self.imap2(enc, dec)(decode)(encode)
      }
    }
  }

}
