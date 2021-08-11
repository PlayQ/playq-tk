package net.playq.tk.rocksdb

import java.util.UUID

trait RocksDBEncoder[T] {
  def toBytes(t: T): Array[Byte]
}

object RocksDBEncoder {
  @inline def apply[T](implicit ev: RocksDBEncoder[T]): ev.type = ev

  def toBytes[T: RocksDBEncoder](t: T): Array[Byte] = RocksDBEncoder[T].toBytes(t)

  implicit val stringRocksDBEncoder: RocksDBEncoder[String] = _.getBytes
  implicit val byteRocksDBEncoder: RocksDBEncoder[Byte]     = BigInt(_).toByteArray
  implicit val shortRocksDBEncoder: RocksDBEncoder[Short]   = BigInt(_).toByteArray
  implicit val intRocksDBEncoder: RocksDBEncoder[Int]       = BigInt(_).toByteArray
  implicit val longRocksDBEncoder: RocksDBEncoder[Long]     = BigInt(_).toByteArray
  implicit val uuidRocksDBEncoder: RocksDBEncoder[UUID]     = _.toString.getBytes
}
