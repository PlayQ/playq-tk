package net.playq.tk.orc

import net.playq.tk.orc.OrcValue.OrcAny
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch

trait OrcEncoder[-Row] { self =>
  def writeColumns(schema: OrcSchema, vectorizedRowBatch: VectorizedRowBatch, rowIdx: Int, row: Row): Unit

  final def contramap[A](f: A => Row): OrcEncoder[A] = new OrcEncoder[A] {
    override def writeColumns(schema: OrcSchema, vectorizedRowBatch: VectorizedRowBatch, rowIdx: Int, row: A): Unit = {
      self.writeColumns(schema, vectorizedRowBatch, rowIdx, f(row))
    }
  }
}

object OrcEncoder {
  @inline def apply[Row: OrcEncoder]: OrcEncoder[Row] = implicitly

  implicit final val orcEncoderOrdered: OrcEncoder[collection.IndexedSeq[OrcAny]] = new OrcValueEncoderBase[collection.IndexedSeq[OrcAny]] {
    override def writeColumns(schema: OrcSchema, vectorizedRowBatch: VectorizedRowBatch, rowIdx: Int, row: collection.IndexedSeq[OrcAny]): Unit = {
      require(vectorizedRowBatch.cols.length == row.size, "vectorized row batch size is not the same as row size")

      writeColumnsImpl(schema, vectorizedRowBatch, rowIdx, row, (_, colIdx, r) => r(colIdx))
    }
  }

  implicit final val orcEncoderWithHeaders: OrcEncoder[collection.Map[String, OrcAny]] = new OrcValueEncoderBase[collection.Map[String, OrcAny]] {
    override def writeColumns(schema: OrcSchema, vectorizedRowBatch: VectorizedRowBatch, rowIdx: Int, row: collection.Map[String, OrcAny]): Unit = {
      writeColumnsImpl(schema, vectorizedRowBatch, rowIdx, row, (column, _, r) => r.getOrElse(column.name, OrcValue.OrcNull))
    }
  }

  abstract class OrcValueEncoderBase[ColIndexableRow] extends OrcEncoder[ColIndexableRow] {
    @inline final protected[this] def writeColumnsImpl(
      schema: OrcSchema,
      vectorizedRowBatch: VectorizedRowBatch,
      rowIdx: Int,
      row: ColIndexableRow,
      accessRow: (OrcColumn, Int, ColIndexableRow) => OrcAny,
    ): Unit = {
      val columns = schema.columns
      val cols    = vectorizedRowBatch.cols
      val sz      = cols.length
      require(columns.size == sz, "vectorized row batch size is not the same as schema size")
      var colIdx = 0
      while (colIdx < sz) {
        val column = columns(colIdx)
        val col    = cols(colIdx)

        val nullableOrcValue = accessRow(column, colIdx, row)
        column.tpe.writeOrThrow(col, rowIdx, nullableOrcValue)

        colIdx += 1
      }
    }
  }

}
