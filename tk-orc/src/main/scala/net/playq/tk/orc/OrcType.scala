package net.playq.tk.orc

import izumi.functional.bio.Clock1
import org.apache.hadoop.hive.ql.exec.vector.*
import org.apache.orc.TypeDescription

import java.sql.Timestamp
import java.time.{LocalDate, OffsetDateTime, ZoneOffset, ZonedDateTime}
import scala.collection.Factory
import scala.util.{Failure, Success, Try}

sealed trait OrcType {
  type InnerType
  def typeDescription: TypeDescription

  def valueOf(t: InnerType): OrcValue[InnerType]

  def read(columnVector: ColumnVector, idx: Int): OrcValue.OrcAny
  @throws[OrcException]
  def writeOrThrow(columnVector: ColumnVector, idx: Int, value: OrcValue.OrcAny): Unit
}

object OrcType {
  @inline def apply[T](implicit otl: OrcTypeLike[T]): otl.type = otl

  final case class OrcReaderException(message: String) extends RuntimeException(message)

  sealed trait OrcTypeLike[T] extends OrcType {
    final override type InnerType = T

    protected[OrcType] def readInnerUnsafe(columnVector: ColumnVector, idx: Int): T
    def writeInner(columnVector: ColumnVector, idx: Int, value: T): Unit

    final def readInner(columnVector: ColumnVector, idx: Int): Option[T] = {
      if (columnVector.isNull(idx)) {
        None
      } else {
        val repIdx = if (columnVector.isRepeating) 0 else idx
        Try(readInnerUnsafe(columnVector, repIdx)) match {
          case Failure(exception) =>
            val columnType = columnVector.getClass.getSimpleName
            val thisType   = this.getClass.getSimpleName
            throw OrcReaderException(s"Failed to read OrcColumn of type [$columnType] as [$thisType]: ${exception.getMessage}.")
          case Success(value) =>
            Some(value)
        }
      }
    }
    final override def read(columnVector: ColumnVector, idx: Int): OrcValue.OrcAny = readInner(columnVector, idx).fold[OrcValue.OrcAny](OrcValue.OrcNull)(valueOf)
    final override def writeOrThrow(columnVector: ColumnVector, idx: Int, value: OrcValue.OrcAny): Unit = {
      value match {
        case OrcValue.OrcNull =>
          columnVector.noNulls     = false
          columnVector.isNull(idx) = true
        case v: OrcValue[?] =>
          writeInner(columnVector, idx, v.asInstanceOf[OrcValue[T]].value)
        case null =>
          throw OrcException.ValueException(s"got wrong value: expected [${this.getClass.getSimpleName}] but got null")
      }
    }
  }

  sealed trait OrcScalar[T] extends OrcTypeLike[T]

  implicit case object int extends OrcScalar[Int] {
    override def typeDescription: TypeDescription                                   = TypeDescription.createInt()
    override def valueOf(t: Int): OrcValue.OrcInt                                   = OrcValue.OrcInt(t)
    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): Int         = long.readInnerUnsafe(columnVector, idx).toInt
    override def writeInner(columnVector: ColumnVector, idx: Int, value: Int): Unit = long.writeInner(columnVector, idx, value.toLong)
  }

  implicit case object long extends OrcScalar[Long] {
    override def typeDescription: TypeDescription                            = TypeDescription.createLong()
    override def valueOf(t: Long): OrcValue.OrcLong                          = OrcValue.OrcLong(t)
    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): Long = {
      (columnVector: @unchecked) match {
        case vector: DoubleColumnVector => vector.vector(idx).toLong
        case vector: LongColumnVector   => vector.vector(idx)
      }
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, value: Long): Unit = {
      columnVector.asInstanceOf[LongColumnVector].vector(idx) = value
    }
  }

  implicit case object double extends OrcScalar[Double] {
    override def typeDescription: TypeDescription                              = TypeDescription.createDouble()
    override def valueOf(t: Double): OrcValue.OrcDouble                        = OrcValue.OrcDouble(t)
    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): Double = {
      (columnVector: @unchecked) match {
        case vector: DoubleColumnVector => vector.vector(idx)
        case vector: LongColumnVector   => vector.vector(idx).toDouble
      }
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, value: Double): Unit = {
      columnVector.asInstanceOf[DoubleColumnVector].vector(idx) = value
    }
  }

  implicit case object float extends OrcScalar[Float] {
    override def typeDescription: TypeDescription                                     = TypeDescription.createFloat()
    override def valueOf(t: Float): OrcValue.OrcFloat                                 = OrcValue.OrcFloat(t)
    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): Float         = double.readInnerUnsafe(columnVector, idx).toFloat
    override def writeInner(columnVector: ColumnVector, idx: Int, value: Float): Unit = double.writeInner(columnVector, idx, value.toDouble)
  }

  implicit case object string extends OrcScalar[String] {
    override def typeDescription: TypeDescription       = TypeDescription.createString()
    override def valueOf(t: String): OrcValue.OrcString = OrcValue.OrcString(t)

    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): String = {
      (columnVector: @unchecked) match {
        case bcv: BytesColumnVector     => new String(bcv.vector(idx), bcv.start(idx), bcv.length(idx))
        case vector: DoubleColumnVector => vector.vector(idx).toString
        case vector: LongColumnVector   => vector.vector(idx).toString
      }
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, value: String): Unit = {
      val bytes = value.getBytes
      columnVector.asInstanceOf[BytesColumnVector].setRef(idx, bytes, 0, bytes.length)
    }
  }

  implicit case object zdt extends OrcScalar[ZonedDateTime] {
    override def typeDescription: TypeDescription           = TypeDescription.createTimestamp()
    override def valueOf(t: ZonedDateTime): OrcValue.OrcTsz = OrcValue.OrcTsz(t)

    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): ZonedDateTime = {
      (columnVector: @unchecked) match {
        case timeColumn: TimestampColumnVector =>
          val timestamp = new Timestamp(timeColumn.time(idx))
          timestamp.setNanos(timeColumn.nanos(idx))
          ZonedDateTime.ofInstant(timestamp.toInstant, Clock1.TZ_UTC)
        case dateColumn: DateColumnVector =>
          LocalDate.ofEpochDay(dateColumn.vector(idx)).atStartOfDay(Clock1.TZ_UTC)
      }
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, value: ZonedDateTime): Unit = {
      val timeColumn = columnVector.asInstanceOf[TimestampColumnVector]
      val timestamp  = Timestamp.from(value.withZoneSameInstant(Clock1.TZ_UTC).toInstant)
      timeColumn.time(idx)  = timestamp.getTime
      timeColumn.nanos(idx) = timestamp.getNanos
      timeColumn.setIsUTC(true)
    }
  }

  implicit case object odt extends OrcScalar[OffsetDateTime] {
    override def typeDescription: TypeDescription            = TypeDescription.createTimestamp()
    override def valueOf(t: OffsetDateTime): OrcValue.OrcOdt = OrcValue.OrcOdt(t)

    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): OffsetDateTime = {
      (columnVector: @unchecked) match {
        case timeColumn: TimestampColumnVector =>
          val timestamp = new Timestamp(timeColumn.time(idx))
          timestamp.setNanos(timeColumn.nanos(idx))
          OffsetDateTime.ofInstant(timestamp.toInstant, ZoneOffset.UTC)
        case dateColumn: DateColumnVector =>
          LocalDate.ofEpochDay(dateColumn.vector(idx)).atStartOfDay(Clock1.TZ_UTC).toOffsetDateTime
      }
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, value: OffsetDateTime): Unit = {
      val timeColumn = columnVector.asInstanceOf[TimestampColumnVector]
      val timestamp  = Timestamp.from(value.withOffsetSameInstant(ZoneOffset.UTC).toInstant)
      timeColumn.time(idx)  = timestamp.getTime
      timeColumn.nanos(idx) = timestamp.getNanos
      timeColumn.setIsUTC(true)
    }
  }

  implicit case object localDate extends OrcScalar[LocalDate] {
    override def typeDescription: TypeDescription        = TypeDescription.createDate()
    override def valueOf(t: LocalDate): OrcValue.OrcDate = OrcValue.OrcDate(t)

    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): LocalDate = {
      (columnVector: @unchecked) match {
        case timeColumn: TimestampColumnVector =>
          val timestamp = new Timestamp(timeColumn.time(idx))
          timestamp.setNanos(timeColumn.nanos(idx))
          ZonedDateTime.ofInstant(timestamp.toInstant, Clock1.TZ_UTC).toLocalDate
        case dateColumn: DateColumnVector =>
          LocalDate.ofEpochDay(dateColumn.vector(idx))
      }
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, value: LocalDate): Unit = {
      columnVector.asInstanceOf[DateColumnVector].vector(idx) = value.toEpochDay
    }
  }

  implicit case object boolean extends OrcScalar[Boolean] {
    override def typeDescription: TypeDescription                               = TypeDescription.createBoolean()
    override def valueOf(t: Boolean): OrcValue.OrcBoolean                       = OrcValue.OrcBoolean(t)
    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): Boolean = {
      columnVector.asInstanceOf[LongColumnVector].vector(idx) == 1
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, value: Boolean): Unit = {
      columnVector.asInstanceOf[LongColumnVector].vector(idx) = if (value) 1L else 0L
    }
  }

  implicit case object binary extends OrcScalar[Array[Byte]] {
    override def typeDescription: TypeDescription                                   = TypeDescription.createBinary()
    override def valueOf(t: Array[Byte]): OrcValue.OrcBinary                        = OrcValue.OrcBinary(t)
    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): Array[Byte] = {
      val bcv         = columnVector.asInstanceOf[BytesColumnVector]
      val startOffset = bcv.start(idx)
      val endOffset   = startOffset + bcv.length(idx)
      java.util.Arrays.copyOfRange(bcv.vector(idx), startOffset, endOffset)
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, value: Array[Byte]): Unit = {
      columnVector.asInstanceOf[BytesColumnVector].setRef(idx, value, 0, value.length)
    }
  }

  final case class OrcList[I[v] <: Iterable[v], V](valueTpe: OrcScalar[V], factory: Factory[V, I[V]]) extends OrcTypeLike[I[V]] {
    override def typeDescription: TypeDescription                            = TypeDescription.createList(valueTpe.typeDescription)
    override def valueOf(t: I[V]): OrcValue.OrcList[V, I]                    = OrcValue.OrcList(t)(using valueTpe)
    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): I[V] = {
      val listValues = columnVector.asInstanceOf[ListColumnVector]
      (listValues.offsets(idx) until listValues.offsets(idx) + listValues.lengths(idx))
        .foldLeft(factory.newBuilder) {
          case (acc, indexRaw) =>
            val index = indexRaw.toInt
            valueTpe.readInner(listValues.child, index).fold(acc)(v => acc ++= Iterable(v))
        }.result()
    }
    override def writeInner(columnVector: ColumnVector, idx: Int, values: I[V]): Unit = {
      val columnList = columnVector.asInstanceOf[ListColumnVector]
      val offset     = columnList.childCount.toLong

      columnList.offsets(idx) = offset
      columnList.lengths(idx) = values.size.toLong
      columnList.childCount   = columnList.childCount + values.size

      columnList.child.ensureSize(columnList.childCount, true)

      values.zipWithIndex.foreach {
        case (v, idx) =>
          val pos = (offset + idx).toInt
          valueTpe.writeInner(columnList.child, pos, v)
      }
    }
  }

  implicit def iterableLike[V, I[v] <: Iterable[v]](
    implicit valueTpe: OrcScalar[V],
    factory: Factory[V, I[V]],
  ): OrcList[I, V] = OrcList(valueTpe, factory)

  final case class OrcMap[K, V, M[k, v] <: Map[k, v]](keyTpe: OrcScalar[K], valueTpe: OrcScalar[V], factory: Factory[(K, V), M[K, V]]) extends OrcTypeLike[M[K, V]] {
    override def typeDescription: TypeDescription = TypeDescription.createMap(keyTpe.typeDescription, valueTpe.typeDescription)

    override def valueOf(t: M[K, V]): OrcValue.OrcMap[K, V, M] = OrcValue.OrcMap(t)(using keyTpe, valueTpe)

    override def readInnerUnsafe(columnVector: ColumnVector, idx: Int): M[K, V] = {
      val mapValues = columnVector.asInstanceOf[MapColumnVector]
      (mapValues.offsets(idx) until mapValues.offsets(idx) + mapValues.lengths(idx))
        .foldLeft(factory.newBuilder) {
          case (acc, indexRaw) =>
            val index = indexRaw.toInt
            (for {
              k <- keyTpe.readInner(mapValues.keys, index)
              v <- valueTpe.readInner(mapValues.values, index)
            } yield k -> v).fold(acc)(t => acc ++= Iterable(t))
        }.result()
    }

    override def writeInner(columnVector: ColumnVector, idx: Int, values: M[K, V]): Unit = {
      val columnMap = columnVector.asInstanceOf[MapColumnVector]
      val offset    = columnMap.childCount.toLong

      columnMap.offsets(idx) = offset
      columnMap.lengths(idx) = values.size.toLong
      columnMap.childCount   = columnMap.childCount + values.size

      columnMap.keys.ensureSize(columnMap.childCount, true)
      columnMap.values.ensureSize(columnMap.childCount, true)

      values.zipWithIndex.foreach {
        case ((k, v), idx) =>
          val pos = (offset + idx).toInt
          keyTpe.writeInner(columnMap.keys, pos, k)
          valueTpe.writeInner(columnMap.values, pos, v)
      }
    }
  }

  implicit def mapLike[K, V, M[k, v] <: Map[k, v]](
    implicit keyTpe: OrcScalar[K],
    valueTpe: OrcScalar[V],
    factory: Factory[(K, V), M[K, V]],
  ): OrcMap[K, V, M] = OrcMap(keyTpe, valueTpe, factory)
}
