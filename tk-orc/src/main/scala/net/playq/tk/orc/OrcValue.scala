package net.playq.tk.orc

import java.time.{LocalDate, OffsetDateTime, ZonedDateTime}
import scala.annotation.unused
import scala.language.implicitConversions

sealed trait OrcValue[+T] {
  def value: T

  def isScalar: Boolean = false

  def asInt: Option[Int]                                                                                                            = None
  def asLong: Option[Long]                                                                                                          = None
  def asDouble: Option[Double]                                                                                                      = None
  def asFloat: Option[Float]                                                                                                        = None
  def asString: Option[String]                                                                                                      = None
  def asLocalDate: Option[LocalDate]                                                                                                = None
  def asTsz: Option[ZonedDateTime]                                                                                                  = None
  def asOdt: Option[OffsetDateTime]                                                                                                 = None
  def asBoolean: Option[Boolean]                                                                                                    = None
  def asBytes: Option[Array[Byte]]                                                                                                  = None
  def asMap[K1, V1](implicit @unused keyTpe0: OrcType.OrcScalar[K1], @unused valueTpe0: OrcType.OrcScalar[V1]): Option[Map[K1, V1]] = None
  def asIterable[V0](implicit @unused valueTpe0: OrcType.OrcScalar[V0]): Option[Iterable[V0]]                                       = None
}

object OrcValue {
  type OrcAny       = OrcValue[Any]
  type OrcScalarAny = OrcScalar[Any]

  sealed trait OrcScalar[+T] extends OrcValue[T] {
    override def isScalar: Boolean = true
  }

  implicit def fromInt(v: Int): OrcInt               = OrcInt(v)
  implicit def fromLong(v: Long): OrcLong            = OrcLong(v)
  implicit def fromDouble(v: Double): OrcDouble      = OrcDouble(v)
  implicit def fromFloat(v: Float): OrcFloat         = OrcFloat(v)
  implicit def fromString(v: String): OrcString      = OrcString(v)
  implicit def fromTsz(v: ZonedDateTime): OrcTsz     = OrcTsz(v)
  implicit def fromBoolean(v: Boolean): OrcBoolean   = OrcBoolean(v)
  implicit def fromBinary(v: Array[Byte]): OrcBinary = OrcBinary(v)

  implicit def fromMap[K, V, M[k, v] <: Map[k, v]](v: M[K, V])(implicit ev1: OrcType.OrcScalar[K], ev2: OrcType.OrcScalar[V]): OrcMap[K, V, M] = OrcMap(v)
  implicit def fromIterable[V, I[v] <: Iterable[v]](v: I[V])(implicit ev1: OrcType.OrcScalar[V]): OrcList[V, I]                                = OrcList(v)

  case object OrcNull extends OrcValue[Null] { override def value: Null = null }

  final case class OrcInt(value: Int) extends OrcScalar[Int] { override def asInt: Some[Int] = Some(value) }
  final case class OrcLong(value: Long) extends OrcScalar[Long] { override def asLong: Some[Long] = Some(value) }
  final case class OrcDouble(value: Double) extends OrcScalar[Double] { override def asDouble: Some[Double] = Some(value) }
  final case class OrcFloat(value: Float) extends OrcScalar[Float] { override def asFloat: Some[Float] = Some(value) }
  final case class OrcString(value: String) extends OrcScalar[String] { override def asString: Some[String] = Some(value) }
  final case class OrcOdt(value: OffsetDateTime) extends OrcScalar[OffsetDateTime] { override def asOdt: Some[OffsetDateTime] = Some(value) }
  final case class OrcTsz(value: ZonedDateTime) extends OrcScalar[ZonedDateTime] { override def asTsz: Some[ZonedDateTime] = Some(value) }
  final case class OrcDate(value: LocalDate) extends OrcScalar[LocalDate] { override def asLocalDate: Some[LocalDate] = Some(value) }
  final case class OrcBoolean(value: Boolean) extends OrcScalar[Boolean] { override def asBoolean: Some[Boolean] = Some(value) }
  final case class OrcBinary(value: Array[Byte]) extends OrcScalar[Array[Byte]] { override def asBytes: Some[Array[Byte]] = Some(value) }

  final case class OrcMap[K, V, M[k, v] <: Map[k, v]](value: M[K, V])(implicit keyTpe: OrcType.OrcScalar[K], valueTpe: OrcType.OrcScalar[V]) extends OrcValue[M[K, V]] {
    // small hack to cast map to the expected one
    override def asMap[K0, V0](implicit keyTpe0: OrcType.OrcScalar[K0], valueTpe0: OrcType.OrcScalar[V0]): Option[Map[K0, V0]] = {
      if (keyTpe0.typeDescription == keyTpe.typeDescription && valueTpe0.typeDescription == valueTpe.typeDescription) {
        Some(value.asInstanceOf[Map[K0, V0]])
      } else {
        None
      }
    }
  }

  final case class OrcList[V, I[v] <: Iterable[v]](value: I[V])(implicit valueTpe: OrcType.OrcScalar[V]) extends OrcValue[I[V]] {
    // small hack to cast iterable to the expected one
    def asOrcValuesIterable: Iterable[OrcValue[V]]                                               = value.map(valueTpe.valueOf)
    override def asIterable[V0](implicit valueTpe0: OrcType.OrcScalar[V0]): Option[Iterable[V0]] = {
      if (valueTpe0.typeDescription == valueTpe.typeDescription) {
        Some(value.asInstanceOf[Iterable[V0]])
      } else {
        None
      }
    }
  }
}
