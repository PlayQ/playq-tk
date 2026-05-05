package net.playq.tk.util.data

import izumi.functional.bio.{F, IO2}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * BE AWARE: this is mutable data structure, so use it wisely.
  * In case you wanna preserve referential transparency you can
  * use ReportWithHeader which is immutable implementation of
  * AbstractReportWithHeader and holds state using Vectors.
  */
final class MutableColumnBasedData[V] private (
  protected val columnNames: ArrayBuffer[String],
  protected val columns: ArrayBuffer[ArrayBuffer[V]],
) extends AbstractColumnBasedData[V] {

  override def header: Vector[String] = columnNames.toVector

  override def updateHeader(f: String => String): MutableColumnBasedData[V] = {
    columnNames.mapInPlace(f)
    this
  }

  override def updateHeaderWithValues(aliases: Map[String, String]): MutableColumnBasedData[V] = {
    columnNames.mapInPlace(h => aliases.getOrElse(h, h))
    this
  }

  override def size: Int = {
    columns.map(_.size).minOption.getOrElse(0)
  }

  override def rows: Iterator[Vector[V]] = {
    Iterator.range(0, size).map(i => columns.map(_(i)).toVector)
  }

  override def addRow(row: Seq[V]): MutableColumnBasedData[V] = {
    if (row.size != columnNames.size) throw AbstractColumnBasedData.IllegalColumnsNumberException
    row.zipWithIndex.foreach { case (v, idx) => columns(idx).append(v) }
    this
  }

  override def ++(other: AbstractColumnBasedData[V]): MutableColumnBasedData[V] = {
    val thisHeader  = header
    val otherHeader = other.header
    if (thisHeader == otherHeader) {
      other.rows.foreach(addRow)
    } else if (thisHeader.toSet == otherHeader.toSet) {
      val indicies = thisHeader.map(otherHeader.indexOf)
      other.rows.foreach(row => addRow(indicies.map(row(_))))
    } else {
      throw AbstractColumnBasedData.IllegalColumnsNumberException
    }
    this
  }

  override def rightJoin(
    other: AbstractColumnBasedData[V]
  )(default: V,
    leftField: String,
    rightField: String,
    leftPrefix: String,
    rightPrefix: String,
  ): AbstractColumnBasedData[V] = {
    other.leftJoin(this)(default, rightField, leftField, rightPrefix, leftField)
  }

  override def leftJoin(
    other: AbstractColumnBasedData[V]
  )(default: V,
    leftField: String,
    rightField: String,
    leftPrefix: String  = "",
    rightPrefix: String = "",
  ): MutableColumnBasedData[V] = {
    // prepare the mapping indexing
    // if there is no fields in one of tables - index would be empty
    // and join result will contain only default values
    val leftFieldColumn = this.getColumn(leftField).getOrElse(Vector.fill(this.size)(default))
    val rightMapping    = other.getColumn(rightField).map(_.zipWithIndex.toMap).getOrElse(Map.empty)
    val joinRowsIndex   = leftFieldColumn.map(rightMapping.get)

    // prepare joining rows, ignoring the right field
    val (joinHeader, joinColumns) = other.header.zipWithIndex.foldLeft(Vector.empty[String], Vector.empty[Vector[V]]) {
      case ((names, values), (rightColumnName, _)) if rightColumnName == rightField =>
        (names, values)
      case ((names, values), (rightColumnName, rightColumnIdx)) =>
        val rightColumn    = other.getColumn(rightColumnIdx).getOrElse(throw AbstractColumnBasedData.MissingColumnException)
        val joinColumn     = joinRowsIndex.map(_.fold(default)(rightColumn.lift(_).getOrElse(throw AbstractColumnBasedData.RowOutOfBoundException)))
        val joinColumnName = _updateHeader(rightColumnName, rightPrefix)
        (names.appended(joinColumnName), values.appended(joinColumn))
    }

    this.updateHeader(_updateHeader(_, leftPrefix))
    this.addColumns(joinHeader, joinColumns)
  }

  override def getRow(idx: Int): Option[Vector[V]] = {
    import cats.syntax.traverse.*
    columns.toVector.traverse(_.lift(idx))
  }

  override def traverseRows[F[+_, +_]: IO2, E](f: Seq[V] => F[E, Unit]): F[E, Unit] = {
    F.traverse_(0 until size) {
      idx => f(columns.map(_(idx)).toVector)
    }
  }

  override def addColumns(colName: Seq[String], values: Seq[Seq[V]]): MutableColumnBasedData[V] = {
    if (colName.size != values.size) throw AbstractColumnBasedData.IllegalColumnsNumberException
    if (values.exists(_.size != size)) throw AbstractColumnBasedData.IllegalColumnSizeException
    columnNames.appendAll(colName)
    columns.appendAll(ArrayBuffer.from(values.map(ArrayBuffer.from)))
    this
  }

  override def addColumn(colName: String, values: Seq[V]): MutableColumnBasedData[V] = {
    if (values.size != size) throw AbstractColumnBasedData.IllegalColumnSizeException
    columnNames.append(colName)
    columns.append(ArrayBuffer.from(values))
    this
  }

  override def addColumnRepeated(colName: String, value: V): MutableColumnBasedData[V] = {
    columnNames.append(colName)
    columns.append(ArrayBuffer.fill(size)(value))
    this
  }

  override def addColumnsRepeated(values: Map[String, V]): MutableColumnBasedData[V] = {
    values.foreach { case (k, v) => addColumnRepeated(k, v) }
    this
  }

  override def getColumn(field: String): Option[Vector[V]] = {
    indexOfColumn(field).flatMap(getColumn)
  }

  override def getColumn(idx: Int): Option[Vector[V]] = {
    if (idx < 0) None else columns.lift(idx).map(_.toVector)
  }

  override def removeColumn(name: String): MutableColumnBasedData[V] = {
    val idx = columnNames.indexOf(name)
    columns.remove(idx)
    columnNames.remove(idx)
    this
  }

  @SuppressWarnings(Array("OptionGet"))
  override def groupBy[G](field: String)(f: V => G): Map[G, MutableColumnBasedData[V]] = {
    indexOfColumn(field).flatMap(columns.lift) match {
      case Some(column) =>
        var idx = 0
        val m   = mutable.Map.empty[G, MutableColumnBasedData[V]]
        column.foreach {
          v =>
            val row = getRow(idx).get
            val g   = f(v)
            m.updateWith(g) {
              case Some(data) =>
                Some(data.addRow(row))
              case None =>
                Some(MutableColumnBasedData.empty[V](columnNames.toSeq).addRow(row))
            }
            idx += 1
        }
        m.toMap
      case None =>
        Map.empty
    }
  }

  private def indexOfColumn(field: String): Option[Int] = {
    Option(columnNames.indexOf(field)).filter(_ >= 0)
  }
}

object MutableColumnBasedData {
  def empty[V](header: Seq[String]): MutableColumnBasedData[V] = {
    new MutableColumnBasedData(ArrayBuffer.from(header), ArrayBuffer.fill(header.size)(ArrayBuffer.empty))
  }

  def apply[V](colNames: List[String], data: List[List[V]]): MutableColumnBasedData[V] = {
    new MutableColumnBasedData(ArrayBuffer.from(colNames), ArrayBuffer.from(data.map(ArrayBuffer.from)))
  }

  def fromCSV(all: List[List[String]]): MutableColumnBasedData[String] = all match {
    case header :: tail => fromRowsWithHeader(header, tail)
    case _              => MutableColumnBasedData(Nil, Nil)
  }

  def fromRowsWithHeader(header: List[String], rows: List[List[String]]): MutableColumnBasedData[String] = {
    val data = MutableColumnBasedData.empty[String](header)
    rows.foreach(data.addRow(_))
    data
  }

  def fromMap[V](all: List[Map[String, V]], onEmpty: V): MutableColumnBasedData[V] = {
    val header = all.flatMap(_.keys).distinct.toVector
    val data   = MutableColumnBasedData.empty[V](header)
    all.foreach {
      map =>
        data.addRow(header.map(map.getOrElse(_, onEmpty)))
    }
    data
  }
}
