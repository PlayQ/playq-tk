package net.playq.tk.util.data.convert

trait IndexedData[-T, V] {
  def get(data: T, name: String): Option[V]
}

object IndexedData {
  implicit def fromMap[V]: IndexedData[Map[String, V], V] = new IndexedData[Map[String, V], V] {
    override def get(data: Map[String, V], name: String): Option[V] = data.get(name)
  }

  implicit def fromSeq[V]: IndexedData[Seq[(String, V)], V] = new IndexedData[Seq[(String, V)], V] {
    override def get(data: Seq[(String, V)], name: String): Option[V] = data.find(_._1 == name).map(_._2)
  }
  def fromSchema[V](schema: Seq[String]): IndexedData[Seq[V], V] = new IndexedData[Seq[V], V] {
    private[this] val index: Map[String, Int]               = schema.zipWithIndex.toMap
    override def get(data: Seq[V], name: String): Option[V] = index.get(name).flatMap(data.lift)
  }
}
