package net.playq.tk.rocksdb

abstract class RocksBaseId[T](val base: String, val dpb: String){
  def updatePath(newBase: String, newDpb: String): T
}
