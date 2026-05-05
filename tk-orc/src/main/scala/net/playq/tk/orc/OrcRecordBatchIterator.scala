package net.playq.tk.orc

import cats.syntax.traverse.*
import fs2.{Chunk, Stream}
import izumi.functional.bio.{F, IO2}
import net.playq.tk.orc.OrcValue.OrcAny
import org.apache.hadoop.hive.ql.exec.vector.{ColumnVector, VectorizedRowBatch}
import org.apache.orc.{Reader, RecordReader, TypeDescription}

import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.chaining.scalaUtilChainingOps

trait OrcRecordBatchIterator extends Iterator[ArraySeq[ArraySeq[OrcAny]]] with AutoCloseable {
  def maybeNext(): Option[ArraySeq[ArraySeq[OrcAny]]]

  def stream[F[+_, +_]: IO2](minBatchSize: Int): Stream[F[Throwable, _], Chunk[ArraySeq[OrcAny]]] = {
    Stream.unfoldEval(this) {
      iter =>
        F.syncThrowable(iter.maybeNextBatch(minBatchSize, Chunk.empty).map(_ -> iter))
    }
  }

  @tailrec
  protected final def maybeNextBatch(minBatchSize: Int, existingBatch: Chunk[ArraySeq[OrcAny]]): Option[Chunk[ArraySeq[OrcAny]]] = {
    maybeNext() match {
      case Some(value) =>
        val combined = existingBatch ++ Chunk.from(value)
        if (combined.size >= minBatchSize) Some(combined) else maybeNextBatch(minBatchSize, combined)
      case None =>
        if (existingBatch.nonEmpty) Some(existingBatch) else None
    }
  }
}

object OrcRecordBatchIterator {
  def create(
    fileReader: Reader,
    readerSchema: TypeDescription,
    userSchema: OrcSchema,
    filters: List[OrcRecordFilter],
    rowBatchSize: Int,
  ): Either[Throwable, OrcRecordBatchIterator] = {
    for {
      batchBuffer <- Right(readerSchema.createRowBatch(rowBatchSize))
      // reindex user defined schema
      columnToIndex <- userSchema.indexColumnsFor(readerSchema)
      // batchVectors are mutable, so it's safe to create a map of them and read on each batch
      batchVectors <- columnToIndex.traverse {
        case (column, idx) =>
          batchBuffer.cols
            .lift(idx).map(column -> _)
            .toRight(OrcException.SchemaException(s"file missing ${column.name} field"))
      }
      recordReader = createRecordReader(fileReader, userSchema, filters)
    } yield {
      new Default(recordReader, batchBuffer, batchVectors)
    }
  }

  final class Default(
    reader: RecordReader,
    batchBuffer: VectorizedRowBatch,
    batchVectors: ArraySeq[(OrcColumn, ColumnVector)],
  ) extends OrcRecordBatchIterator {
    private[this] val finished = new AtomicBoolean(false)

    @inline override def hasNext: Boolean = !finished.get()

    @inline override def next(): ArraySeq[ArraySeq[OrcAny]] = {
      // it appears that `reader.getProgress` returns only the progress of the current stripe (file section)
      // and using it to determine if reading is finished might cause a records loss
      if (reader.nextBatch(batchBuffer)) {
        // .to(ArraySeq) is efficient here because knownSize is defined for both ArrayIterator and Iterator.range
        if (batchBuffer.selectedInUse) {
          batchBuffer.selected.iterator.take(batchBuffer.getSelectedSize).map(readRow(batchVectors)).to(ArraySeq)
        } else {
          Iterator.range(0, batchBuffer.size).map(readRow(batchVectors)).to(ArraySeq)
        }
      } else {
        finished.set(true)
        ArraySeq.empty
      }
    }

    @inline override def maybeNext(): Option[ArraySeq[ArraySeq[OrcAny]]] = {
      Option.when(!finished.get())(next()).filter(_.nonEmpty)
    }

    override def close(): Unit = {
      reader.close()
      resetVectors()
    }

    private def resetVectors(): Unit = {
      batchVectors.foreach(_._2.reset())
      batchBuffer.reset()
    }
  }

  @inline private[this] def createRecordReader(fileReader: Reader, userSchema: OrcSchema, filters: List[OrcRecordFilter]): RecordReader = {
    val opts = new Reader.Options(OrcReader.defaultConfiguration)
      .schema(fileReader.getSchema)
      .pipe(fileReaderSetFilters(fileReader, filters))
      .pipe(fileReaderSetInclude(fileReader, userSchema))

    fileReader.rows(opts)
  }

  @inline private[this] def fileReaderSetInclude(fileReader: Reader, userSchema: OrcSchema)(opts: Reader.Options): Reader.Options = {
    // filter schema to read only fields set by user
    val fileSchema = fileReader.getSchema
    fileSchema.getCategory match {
      case TypeDescription.Category.STRUCT =>
        val fields          = fileSchema.getChildren.asScala.zip(fileSchema.getFieldNames.asScala).toArray
        val fieldsSelection = fields.flatMap {
          case (field, fieldName) =>
            // we should extract nested columns (e.g. maps and lists have nested columns)
            val numOfColumns = 1 + (field.getMaximumId - field.getId)
            val isSelected   = userSchema.columnsByNameUnordered.contains(fieldName)
            Array.fill(numOfColumns)(isSelected)
        }
        // prepend struct select (as all the fields are nested)
        val select = fieldsSelection.prepended(true)
        // do not set include if we are requesting all fields
        if (select.forall(identity)) opts else opts.include(select)
      case _ =>
        opts
    }
  }

  @inline private[this] def fileReaderSetFilters(fileReader: Reader, filters: List[OrcRecordFilter])(opts: Reader.Options): Reader.Options = {
    if (filters.nonEmpty) {
      // timestamp columns should be initialized read on first stripe
      // to avoid `TimestampTreeReader.threadLocalDateFormat` null value caused by thread shift
      // that's rather a workaround then a fix
      val timestampColumns = extractTimestampFields(fileReader.getSchema)
      // nested fields names should be added to filter
      // for `Map` we should add `_key` and `_value` and for `List` we should add `_elem` fields
      // This behaviour is not documented anywhere but can be found here:
      // [org.apache.orc.impl.ParserUtils.findColumn]
      val filteredColumns = filters.toArray.flatMap {
        filter =>
          val name = filter.field.name
          filter.field.tpe match {
            case _: OrcType.OrcScalar[?]    => Seq(name)
            case _: OrcType.OrcList[?, ?]   => Seq(name, s"$name._elem")
            case _: OrcType.OrcMap[?, ?, ?] => Seq(name, s"$name._key", s"$name._value")
          }
      }
      val filterColumns  = (timestampColumns ++ filteredColumns).distinct
      val filterCallback = OrcRecordFilter.createFilter(filters)
      opts.useSelected(true).setRowFilter(filterColumns, filterCallback)
    } else {
      opts
    }
  }

  // extracts all timestamp fields and its parents if needed (nested fields will be checked with depth == 1)
  @inline private[this] def extractTimestampFields(field: TypeDescription): Array[String] = {
    field.getCategory match {
      case TypeDescription.Category.STRUCT =>
        field.getChildren.asScala.toArray.flatMap(extractTimestampFields)
      case TypeDescription.Category.TIMESTAMP =>
        Array(field.getFullFieldName)
      case _ if field.getChildren != null =>
        val children = field.getChildren.asScala.toArray
        if (children.exists(_.getCategory == TypeDescription.Category.TIMESTAMP)) {
          children.map(_.getFullFieldName).prepended(field.getFullFieldName)
        } else {
          Array.empty
        }
      case _ => Array.empty
    }
  }

  @inline private[this] def readRow(batchVector: ArraySeq[(OrcColumn, ColumnVector)])(index: Int): ArraySeq[OrcAny] = {
    batchVector.map { case (column, columnVector) => column.tpe.read(columnVector, index) }
  }
}
