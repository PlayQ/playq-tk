package net.playq.tk.rocksdb

trait RocksDBEncoder[T] {
  def toBytes(t: T): Array[Byte]
}

object RocksDBEncoder {
  @inline def apply[T](implicit ev: RocksDBEncoder[T]): ev.type = ev

  def toBytes[T: RocksDBEncoder](t: T): Array[Byte] = RocksDBEncoder[T].toBytes(t)

  implicit val stringKeyEncoder: RocksDBEncoder[String] = _.getBytes
}