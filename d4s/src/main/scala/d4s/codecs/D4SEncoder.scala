package d4s.codecs

import net.playq.tk.util.magnoliacompat.MagnoliaCompat.*
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.util.UUID
import scala.jdk.CollectionConverters.*

trait D4SEncoder[T] {
  def encode(item: T): AttributeValue

  def contramap[T1](f: T1 => T): D4SEncoder[T1]                                       = item => encode(f(item))
  def postprocessAttributeEncoder(f: AttributeValue => AttributeValue): D4SEncoder[T] = item => f(encode(item))
}

object D4SEncoder extends D4SEncoderDerivation(false) {

  object WithoutNulls extends D4SEncoderDerivation(true)

  trait AsObject[A] extends D4SEncoder[A] {
    def encodeObject(item: A): Map[String, AttributeValue]

    override final def encode(item: A): AttributeValue                 = AttributeValue.builder().m(encodeObjectJava(item)).build()
    override final def contramap[B](f: B => A): D4SEncoder.AsObject[B] = item => encodeObject(f(item))

    final def encodeObjectJava(item: A): java.util.Map[String, AttributeValue] = encodeObject(item).asJava

    final def contramap2[B, C](another: D4SEncoder.AsObject[B])(f: C => (A, B)): D4SEncoder.AsObject[C] = {
      item =>
        val (a, b) = f(item)
        encodeObject(a) ++ another.encodeObject(b)
    }
    final def postprocessObjectEncoder(f: Map[String, AttributeValue] => Map[String, AttributeValue]): D4SEncoder.AsObject[A] = item => f(encodeObject(item))

    def appendFields[Item: D4SEncoder.AsObject](f: (A, Map[String, AttributeValue]) => Item): D4SEncoder.AsObject[A] = {
      item =>
        val encoded = encodeObject(item)
        encoded ++ D4SEncoder.AsObject.encode(f(item, encoded))
    }
  }
  object AsObject extends D4SEncoderAsObjectDerivation(false) {

    object WithoutNulls extends D4SEncoderAsObjectDerivation(true)

    @inline def apply[A](implicit ev: D4SEncoder.AsObject[A]): ev.type = ev

    def encode[A: D4SEncoder.AsObject](item: A): Map[String, AttributeValue]               = D4SEncoder.AsObject[A].encodeObject(item)
    def encodeJava[A: D4SEncoder.AsObject](item: A): java.util.Map[String, AttributeValue] = D4SEncoder.AsObject[A].encodeObjectJava(item)
  }

  @inline def apply[A](implicit ev: D4SEncoder[A]): ev.type = ev

  def encode[T: D4SEncoder](item: T): AttributeValue                                 = D4SEncoder[T].encode(item)
  def encodeField[T: D4SEncoder](name: String, item: T): Map[String, AttributeValue] = Map(name -> D4SEncoder[T].encode(item))

  implicit val attributeEncoder: D4SEncoder[AttributeValue] = a => a
  implicit val stringEncoder: D4SEncoder[String]            = AttributeValue.builder().s(_).build()
  implicit val boolEncoder: D4SEncoder[Boolean]             = AttributeValue.builder().bool(_).build()
  implicit val byteEncoder: D4SEncoder[Byte]                = numericAttributeEncoder[Byte]
  implicit val shortEncoder: D4SEncoder[Short]              = numericAttributeEncoder[Short]
  implicit val intEncoder: D4SEncoder[Int]                  = numericAttributeEncoder[Int]
  implicit val longEncoder: D4SEncoder[Long]                = numericAttributeEncoder[Long]
  implicit val floatEncoder: D4SEncoder[Float]              = numericAttributeEncoder[Float]
  implicit val doubleEncoder: D4SEncoder[Double]            = numericAttributeEncoder[Double]
  implicit val unitEncoder: D4SEncoder[Unit]                = _ => AttributeValue.builder().m(Map.empty[String, AttributeValue].asJava).build()
  implicit val uuidEncoder: D4SEncoder[UUID]                = n => AttributeValue.builder().s(n.toString).build()
  implicit val bytesEncoder: D4SEncoder[Array[Byte]]        = n => AttributeValue.builder().b(SdkBytes.fromByteArray(n)).build()
  implicit val sdkBytesEncoder: D4SEncoder[SdkBytes]        = n => AttributeValue.builder().b(n).build()

  implicit val binarySetSdkBytesEncoder: D4SEncoder[Set[SdkBytes]] =
    item => AttributeValue.builder().bs(item.asJavaCollection).build()

  implicit val binarySetEncoder: D4SEncoder[Set[Array[Byte]]] =
    item => AttributeValue.builder().bs(item.map(SdkBytes.fromByteArray).asJavaCollection).build()

  implicit val stringSetEncoder: D4SEncoder[Set[String]] =
    item => AttributeValue.builder().ss(item.asJavaCollection).build()

  implicit val byteSetEncoder: D4SEncoder[Set[Byte]]     = item => AttributeValue.builder().ns(item.map(_.toString).asJavaCollection).build()
  implicit val shortSetEncoder: D4SEncoder[Set[Short]]   = item => AttributeValue.builder().ns(item.map(_.toString).asJavaCollection).build()
  implicit val intSetEncoder: D4SEncoder[Set[Int]]       = item => AttributeValue.builder().ns(item.map(_.toString).asJavaCollection).build()
  implicit val longSetEncoder: D4SEncoder[Set[Long]]     = item => AttributeValue.builder().ns(item.map(_.toString).asJavaCollection).build()
  implicit val floatSetEncoder: D4SEncoder[Set[Float]]   = item => AttributeValue.builder().ns(item.map(_.toString).asJavaCollection).build()
  implicit val doubleSetEncoder: D4SEncoder[Set[Double]] = item => AttributeValue.builder().ns(item.map(_.toString).asJavaCollection).build()

  implicit def stringTupleEncoder[S <: String, V: D4SEncoder]: D4SEncoder.AsObject[(S, V)] = {
    case (k, v) => D4SEncoder.encodeField(k, v)
  }

  implicit def eitherEncoder[A: D4SEncoder, B: D4SEncoder]: D4SEncoder.AsObject[Either[A, B]] = {
    D4SEncoder.AsObject.derived[Either[A, B]]
  }

  implicit def iterableEncoder[L[_], T: D4SEncoder](implicit ev: L[T] <:< Iterable[T]): D4SEncoder[L[T]] = {
    (item: L[T]) =>
      val ls = item.map(encode[T])
      AttributeValue.builder().l(ls.asJavaCollection).build()
  }

  implicit def mapLikeEncoder[M[k, v] <: scala.collection.Map[k, v], K: D4SKeyEncoder, V: D4SEncoder]: D4SEncoder.AsObject[M[K, V]] = {
    _.map { case (k, v) => D4SKeyEncoder.encode(k) -> encode(v) }.toMap
  }

  implicit def optionEncoder[T: D4SEncoder]: D4SEncoder[Option[T]] = {
    (item: Option[T]) =>
      item.map(encode[T]).getOrElse(AttributeValue.builder().nul(true).build())
  }

  private[this] def numericAttributeEncoder[NumericType]: D4SEncoder[NumericType] = n => AttributeValue.builder().n(n.toString).build()

}

private[codecs] abstract class D4SEncoderDerivation(dropNulls: Boolean) extends D4SEncoderVersionSpecific {

  /** Magnolia instances */
  type Typeclass[T] = D4SEncoder[T]

  def join[T](ctx: ReadOnlyCaseClass[D4SEncoder, T]): D4SEncoder[T] = {
    if (ctx.isObject) {
      new CaseObjectEncoder[T](ctx.typeName.short)
    } else {
      item =>
        val result = ctx.parameters.map {
          p =>
            p.label -> p.typeclass.encode(p.dereference(item))
        }.toMap
        val map = if (dropNulls) result.view.filter { case (_, v) => !v.nul() }.toMap else result
        AttributeValue.builder().m(map.asJava).build()
    }
  }

  def split[T](ctx: SealedTrait[D4SEncoder, T]): D4SEncoder[T] = {
    traitEncoder[T](ctx.split(_)(subtype => subtype.typeName.short -> subtype.typeclass))
  }

  @SuppressWarnings(Array("IsInstanceOf"))
  private[codecs] def traitEncoder[A](caseMap: A => (String, D4SEncoder[? <: A])): D4SEncoder[A] = {
    item =>
      val typeNameEncoder = caseMap(item)
      if (typeNameEncoder._2.isInstanceOf[CaseObjectEncoder[?]]) {
        typeNameEncoder._2.asInstanceOf[D4SEncoder[A]].encode(item)
      } else {
        AttributeValue.builder().m(Map(typeNameEncoder._1 -> typeNameEncoder._2.asInstanceOf[D4SEncoder[A]].encode(item)).asJava).build()
      }
  }

  private[this] final class CaseObjectEncoder[T](name: String) extends D4SEncoder[T] {
    override def encode(item: T): AttributeValue = AttributeValue.builder().s(name).build()
  }

}

private[codecs] abstract class D4SEncoderAsObjectDerivation(dropNulls: Boolean) extends D4SEncoderAsObjectVersionSpecific {
  /** Magnolia instances. */
  type Typeclass[T] = D4SEncoder[T]

  def join[T](ctx: ReadOnlyCaseClass[D4SEncoder, T]): D4SEncoder.AsObject[T] = {
    item =>
      val result = ctx.parameters.map {
        p =>
          p.label -> p.typeclass.encode(p.dereference(item))
      }.toMap
      if (dropNulls) result.view.filter { case (_, v) => !v.nul() }.toMap else result
  }

  def split[T](ctx: SealedTrait[D4SEncoder, T]): D4SEncoder.AsObject[T] = {
    traitEncoder[T](ctx.split(_)(subtype => subtype.typeName.short -> subtype.typeclass))
  }

  private[codecs] def traitEncoder[A](caseMap: A => (String, D4SEncoder[? <: A])): D4SEncoder.AsObject[A] = {
    item =>
      val typeNameEncoder = caseMap(item)
      Map(typeNameEncoder._1 -> typeNameEncoder._2.asInstanceOf[D4SEncoder[A]].encode(item))
  }
}
