package net.playq.tk.util.data

import izumi.functional.bio.{F, IO2}

import scala.collection.mutable

@SuppressWarnings(Array("OptionGet"))
final class ColumnBasedData[V] private (columnNames: Vector[String], columns: Vector[Vector[V]]) extends AbstractColumnBasedData[V] {
  self =>

  private val columnToIndex: Map[String, Int] = columnNames.zipWithIndex.toMap

  override val header: Vector[String] = columnNames

  override def updateHeader(f: String => String): ColumnBasedData[V] = {
    new ColumnBasedData(columnNames.map(f), columns)
  }

  override def updateHeaderWithValues(aliases: Map[String, String]): ColumnBasedData[V] = {
    new ColumnBasedData(columnNames.map(h => aliases.getOrElse(h, h)), columns)
  }

  override def size: Int = columns.map(_.size).minOption.getOrElse(0)

  override def rows: Iterator[Vector[V]] = {
    Iterator.range(0, size).map(i => columns.map(_(i)))
  }

  @SuppressWarnings(Array("CollectionIndexOnNonIndexedSeq"))
  override def addRow(row: Seq[V]): ColumnBasedData[V] = {
    new ColumnBasedData(columnNames, columns.indices.toVector.map(i => columns(i).appended(row(i))))
  }

  override def ++(other: AbstractColumnBasedData[V]): ColumnBasedData[V] = {
    val thisHeader  = header
    val otherHeader = other.header
    if (thisHeader == otherHeader) {
      val col = header.indices.toVector.map(i => getColumn(i).get ++ other.getColumn(i).get)
      new ColumnBasedData(header, col)
    } else if (thisHeader.toSet == otherHeader.toSet) {
      val indicies = thisHeader.map(otherHeader.indexOf)
      val col      = header.indices.toVector.map(i => getColumn(i).get ++ other.getColumn(indicies(i)).get)
      new ColumnBasedData(header, col)
    } else {
      throw AbstractColumnBasedData.IllegalColumnsNumberException
    }
  }

  override def rightJoin(
    other: AbstractColumnBasedData[V]
  )(default: V,
    leftField: String,
    rightField: String,
    leftPrefix: String,
    rightPrefix: String,
  ): AbstractColumnBasedData[V] = {
    other.leftJoin(this)(default, rightField, leftField, rightPrefix, leftPrefix)
  }

  override def leftJoin(
    other: AbstractColumnBasedData[V]
  )(default: V,
    leftField: String,
    rightField: String,
    leftPrefix: String  = "",
    rightPrefix: String = "",
  ): ColumnBasedData[V] = {
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
    columns.traverse(_.lift(idx))
  }

  override def traverseRows[F[+_, +_]: IO2, E](f: Seq[V] => F[E, Unit]): F[E, Unit] = {
    F.traverse_(0 until size)(idx => f(columns.map(_(idx))))
  }

  override def addColumns(colName: Seq[String], values: Seq[Seq[V]]): ColumnBasedData[V] = {
    if (colName.size != values.size) throw AbstractColumnBasedData.IllegalColumnsNumberException
    if (values.exists(_.size != size)) throw AbstractColumnBasedData.IllegalColumnSizeException
    val newHeader = columnNames ++ colName
    val newRows   = columns.appendedAll(values.map(_.toVector).toVector)
    new ColumnBasedData(newHeader, newRows)
  }

  override def addColumn(colName: String, values: Seq[V]): ColumnBasedData[V] = {
    if (values.size != size) throw AbstractColumnBasedData.IllegalColumnSizeException
    val newHeader = columnNames.appended(colName)
    val newRows   = columns.appended(values.toVector)
    new ColumnBasedData(newHeader, newRows)
  }

  override def addColumnRepeated(colName: String, value: V): ColumnBasedData[V] = {
    val newHeader = columnNames.appended(colName)
    val newRows   = columns.appended(Vector.fill(size)(value))
    new ColumnBasedData(newHeader, newRows)
  }

  override def addColumnsRepeated(values: Map[String, V]): ColumnBasedData[V] = {
    val keys      = values.keys
    val newHeader = columnNames.appendedAll(keys)
    val newRows   = columns.appendedAll(keys.map(k => Vector.fill(size)(values(k))))
    new ColumnBasedData(newHeader, newRows)
  }

  override def getColumn(field: String): Option[Vector[V]] = {
    Option(columnNames.indexOf(field)).filter(_ > 0).flatMap(getColumn)
  }

  override def getColumn(idx: Int): Option[Vector[V]] = {
    if (idx < 0) None else columns.lift(idx)
  }

  override def removeColumn(name: String): ColumnBasedData[V] = {
    val index     = columnToIndex(name)
    val after     = index + 1
    val newHeader = columnNames.slice(0, index) ++ columnNames.slice(after, header.size)
    val newData   = columns.slice(0, index) ++ columns.slice(after, header.size)
    new ColumnBasedData(newHeader, newData)
  }

  override def groupBy[G](field: String)(f: V => G): Map[G, ColumnBasedData[V]] = {
    val index = columnToIndex.getOrElse(field, -1)
    columns.lift(index) match {
      case Some(column) =>
        var idx = 0
        val m   = mutable.Map.empty[G, ColumnBasedData[V]]
        column.foreach {
          v =>
            val row = getRow(idx).get
            val g   = f(v)
            m.updateWith(g) {
              case Some(data) =>
                Some(data.addRow(row))
              case None =>
                Some(ColumnBasedData.empty[V](columnNames).addRow(row))
            }
            idx += 1
        }
        m.toMap
      case None =>
        Map.empty
    }
  }
}

object ColumnBasedData {
  def empty[V](header: Seq[String]): ColumnBasedData[V] = {
    new ColumnBasedData[V](header.toVector, Vector.fill(header.size)(Vector.empty))
  }

  def apply[V](colNames: List[String], data: List[List[V]]): ColumnBasedData[V] = {
    new ColumnBasedData(colNames.toVector, data.map(_.toVector).toVector)
  }

  def fromCSV(all: List[List[String]]): ColumnBasedData[String] = all match {
    case header :: tail => fromRowsWithHeader(header, tail)
    case _              => ColumnBasedData(Nil, Nil)
  }

  @SuppressWarnings(Array("CollectionIndexOnNonIndexedSeq"))
  def fromRowsWithHeader[V](header: List[String], rows: List[List[V]]): ColumnBasedData[V] = {
    val columns = header.indices.map(idx => rows.map(_(idx))).toList
    ColumnBasedData[V](header, columns)
  }

  def fromMap[V](all: List[Map[String, V]])(onEmpty: V): ColumnBasedData[V] = {
    val keys    = all.flatMap(_.keys).distinct
    val columns = keys.map(k => all.map(row => row.getOrElse(k, onEmpty)))
    ColumnBasedData(keys, columns)
  }
}
