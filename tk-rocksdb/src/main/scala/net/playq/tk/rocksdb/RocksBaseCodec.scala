package net.playq.tk.rocksdb

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}
import scala.language.implicitConversions

trait RocksBaseDecoder[T] extends RocksBaseInstances {
  def fromBytes(b: Array[Byte]): T

  def fromString(b: String): T = fromBytes(Base64.getDecoder.decode(b.getBytes(StandardCharsets.UTF_8)))
}
object RocksBaseDecoder {
  @inline def apply[T](implicit ev: RocksBaseDecoder[T]): ev.type = ev
}

trait RocksBaseEncoder[T] extends RocksBaseInstances {
  def toBytes(t: T): Array[Byte]

  def toString(t: T): String = new String(Base64.getEncoder.encode(toBytes(t)), StandardCharsets.UTF_8)
}
object RocksBaseEncoder {
  @inline def apply[T](implicit ev: RocksBaseEncoder[T]): ev.type = ev
}

trait RocksBaseCodec[T] extends RocksBaseDecoder[T] with RocksBaseEncoder[T] {
  def imap[U](to: T => U)(from: U => T): RocksBaseCodec[U] = RocksBaseCodec.of[U](toBytes `apply` from(_))(to `apply` fromBytes(_))
}

object RocksBaseCodec {
  @inline def apply[T](implicit ev: RocksBaseCodec[T]): ev.type = ev

  def toBytes[T: RocksBaseEncoder](t: T): Array[Byte] = RocksBaseEncoder[T].toBytes(t)
  def toString[T: RocksBaseEncoder](t: T): String     = RocksBaseEncoder[T].toString(t)

  def fromBytes[T: RocksBaseDecoder](t: Array[Byte]): T = RocksBaseDecoder[T].fromBytes(t)
  def fromString[T: RocksBaseDecoder](t: String): T     = RocksBaseDecoder[T].fromString(t)

  def of[T](to: T => Array[Byte])(from: Array[Byte] => T): RocksBaseCodec[T] = new RocksBaseCodec[T] {
    override def toBytes(t: T): Array[Byte]   = to(t)
    override def fromBytes(b: Array[Byte]): T = from(b)
  }

  sealed abstract class AnyRocksValue {
    type T
    val value: T
    val codec: RocksBaseEncoder[T]
  }
  object AnyRocksValue {
    @inline implicit final def apply[T0](value0: T0)(implicit codec0: RocksBaseEncoder[T0]): AnyRocksValue = {
      new AnyRocksValue {
        override type T = T0
        override val value: T                   = value0
        override val codec: RocksBaseEncoder[T] = codec0
      }
    }
  }

}

sealed trait RocksBaseInstances
object RocksBaseInstances extends LowPriorityRocksBaseInstances {

  implicit val stringRocksDBEncoder: RocksBaseCodec[String] = new RocksBaseCodec[String] {
    override def toBytes(t: String): Array[Byte]   = t.getBytes(StandardCharsets.UTF_8)
    override def fromBytes(b: Array[Byte]): String = new String(b, StandardCharsets.UTF_8)
    override def toString(t: String): String       = t
    override def fromString(b: String): String     = b
  }

}

sealed trait LowPriorityRocksBaseInstances {

  implicit final val bytesRocksDBEncoder: RocksBaseCodec[Array[Byte]] = {
    RocksBaseCodec.of[Array[Byte]](identity)(identity)
  }

  implicit val uuidRocksCodec: RocksBaseCodec[UUID] = new RocksBaseCodec[UUID] {
    override def toBytes(t: UUID): Array[Byte] = {
      ByteBuffer
        .allocate(2 * java.lang.Long.BYTES)
        .putLong(t.getMostSignificantBits)
        .putLong(t.getLeastSignificantBits)
        .array()
    }
    override def fromBytes(b: Array[Byte]): UUID = {
      val bb = ByteBuffer.wrap(b, 0, 2 * java.lang.Long.BYTES)
      new UUID(bb.getLong(), bb.getLong())
    }
  }

  implicit def optionRocksDecoder[T: RocksBaseDecoder]: RocksBaseDecoder[Option[T]] = {
    bytes =>
      if (bytes.headOption.contains(1.toByte)) {
        Some(RocksBaseCodec.fromBytes[T](bytes.drop(1)))
      } else {
        None
      }
  }

  implicit def optionRocksEncoder[T: RocksBaseEncoder]: RocksBaseEncoder[Option[T]] = {
    case None    => Array[Byte](0.toByte)
    case Some(t) => Array[Byte](1.toByte) ++ RocksBaseCodec.toBytes[T](t)
  }

}
