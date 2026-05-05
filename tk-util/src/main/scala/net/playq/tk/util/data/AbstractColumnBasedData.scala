package net.playq.tk.util.data

import izumi.functional.bio.IO2

trait AbstractColumnBasedData[V] {
  def header: Vector[String]
  def updateHeader(f: String => String): AbstractColumnBasedData[V]
  def updateHeaderWithValues(aliases: Map[String, String]): AbstractColumnBasedData[V]

  def size: Int
  def rows: Iterator[Vector[V]]
  def addRow(row: Seq[V]): AbstractColumnBasedData[V]
  def getRow(idx: Int): Option[Vector[V]]
  def traverseRows[F[+_, +_]: IO2, E](f: Seq[V] => F[E, Unit]): F[E, Unit]
  def ++(other: AbstractColumnBasedData[V]): AbstractColumnBasedData[V]

  def leftJoin(other: AbstractColumnBasedData[V])(default: V, leftField: String, rightField: String, leftPrefix: String, rightPrefix: String): AbstractColumnBasedData[V]
  def rightJoin(other: AbstractColumnBasedData[V])(default: V, leftField: String, rightField: String, leftPrefix: String, rightPrefix: String): AbstractColumnBasedData[V]

  def addColumns(colName: Seq[String], values: Seq[Seq[V]]): AbstractColumnBasedData[V]
  def addColumn(colName: String, values: Seq[V]): AbstractColumnBasedData[V]
  def addColumnRepeated(colName: String, value: V): AbstractColumnBasedData[V]
  def addColumnsRepeated(values: Map[String, V]): AbstractColumnBasedData[V]
  def getColumn(field: String): Option[Vector[V]]
  def getColumn(idx: Int): Option[Vector[V]]
  def removeColumn(name: String): AbstractColumnBasedData[V]

  def groupBy[G](field: String)(f: V => G): Map[G, AbstractColumnBasedData[V]]

  final def isEmpty: Boolean  = size <= 0
  final def nonEmpty: Boolean = size > 0

  protected def _updateHeader(header: String, prefix: String): String = {
    if (header.startsWith(prefix)) header else s"${prefix}_$header"
  }
}

object AbstractColumnBasedData {
  final val IllegalColumnSizeException    = new Throwable("Number of elements in the column must be equal to number of rows in the report.")
  final val IllegalColumnsNumberException = new Throwable("Number of columns in raw must be equal to number of elements in header.")
  final val MissingColumnException        = new Throwable("Column does not exists.")
  final val RowOutOfBoundException        = new Throwable("Row index out of bounds.")
}
