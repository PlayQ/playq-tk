package net.playq.tk.orc

import distage.Lifecycle
import fs2.Stream
import izumi.functional.bio.{F, IO2}
import net.playq.tk.orc.OrcValue.OrcAny
import net.playq.tk.quantified.AsyncThrowable
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch
import org.apache.orc.{OrcConf, OrcFile, TypeDescription, Writer}

import java.io.File

trait OrcWriter[F[_, _], -Row] {
  /** @param batchSize Recommended 1024 [[VectorizedRowBatch.DEFAULT_SIZE]] */
  def write(rows: Iterable[Row], batchSize: Int): F[Throwable, Unit]

  final def writeStream(rows: Stream[F[Throwable, _], Row], batchSize: Int)(implicit FA: AsyncThrowable[F]): F[Throwable, Unit] = {
    rows
      .chunkN(batchSize)
      .evalTap {
        batch =>
          FA.delay(_writeSync(batch.asSeq, batchSize))
      }
      .compile.drain
  }

  def _writeSync(rows: Iterable[Row], batchSize: Int): Unit
  def _writeSync0(vectorizedRowBatch: VectorizedRowBatch, rows: Iterable[Row], batchSize: Int): Unit
}

/**
  * @note OrcWriters do not require `BlockingIO` because they use a lot of CPU heavy compute.
  *       Wrap writes with `F.shiftBlocking` manually if required.
  */
object OrcWriter {
  // configuration creation is pretty slow, so we will use singleton value
  private[orc] lazy val defaultConfiguration: Configuration = {
    val config = new Configuration()
    config.setBoolean(OrcConf.OVERWRITE_OUTPUT_FILE.getAttribute, true)

    // todo: java 25 workaround
    config.set("fs.file.impl", classOf[org.apache.hadoop.fs.LocalFileSystem].getName)
    config.setBoolean("fs.file.impl.disable.cache", true)

    config
  }

  def open[F[+_, +_]: IO2, Row](file: File, schema: OrcSchema.Typed[Row]): Lifecycle[F[Throwable, _], OrcWriter[F, Row]] = {
    openRaw(file, schema: OrcSchema).map(new OrcWriter.Impl[F, Row](_, schema, schema.encoder))
  }

  def open[F[+_, +_]: IO2](file: File, schema: OrcSchema): Lifecycle[F[Throwable, _], OrcWriter.Default[F]] = {
    openRaw(file, schema).map(new OrcWriter.Impl(_, schema, implicitly))
  }

  def openWithHeader[F[+_, +_]: IO2](file: File, schema: OrcSchema): Lifecycle[F[Throwable, _], OrcWriter.WithHeader[F]] = {
    openRaw(file, schema).map(new OrcWriter.Impl(_, schema, implicitly))
  }

  def openRaw[F[+_, +_]: IO2](file: File, schema: OrcSchema): Lifecycle[F[Throwable, _], Writer] = {
    Lifecycle.fromAutoCloseable(createWriter(new Path(file.getAbsolutePath), schema.typeDescription))
  }

  private def createWriter[F[+_, +_]: IO2](path: Path, typeDescription: TypeDescription): F[Throwable, Writer] = F.syncThrowable {
    OrcFile.createWriter(path, OrcFile.writerOptions(defaultConfiguration).setSchema(typeDescription))
  }

  type Default[F[+_, +_]] = OrcWriter[F, collection.IndexedSeq[OrcAny]]

  type WithHeader[F[+_, +_]] = OrcWriter[F, collection.Map[String, OrcAny]]

  final class Impl[F[+_, +_]: IO2, -Row](
    writer: Writer,
    schema: OrcSchema,
    encoder: OrcEncoder[Row],
  ) extends OrcWriter[F, Row] {

    override def write(rows: Iterable[Row], batchSize: Int): F[Throwable, Unit] = F.syncThrowable {
      _writeSync(rows, batchSize)
    }

    override def _writeSync(allRows: Iterable[Row], batchSize: Int): Unit = {
      val vectorizedRowBatch: VectorizedRowBatch = writer.getSchema.createRowBatch(batchSize)
      _writeSync0(vectorizedRowBatch, allRows, batchSize)
    }

    @inline override def _writeSync0(vectorizedRowBatch: VectorizedRowBatch, allRows: Iterable[Row], batchSize: Int): Unit = {
      val batchIter = {
        val knSz = allRows.knownSize
        if (knSz >= 0 && knSz <= batchSize) Iterator(allRows) else allRows.grouped(batchSize)
      }
      batchIter.foreach {
        batch =>
          vectorizedRowBatch.reset()

          val rowIter = batch.iterator
          var rowIdx  = 0
          while (rowIter.hasNext) {
            val row = rowIter.next()
            require(rowIdx < batchSize, s"impossible happened: rowIdx=$rowIdx >= batchSize=$batchSize, please report bug")
            encoder.writeColumns(schema, vectorizedRowBatch, rowIdx, row)

            rowIdx += 1
          }

          vectorizedRowBatch.size = rowIdx

          writer.addRowBatch(vectorizedRowBatch)
      }
    }
  }

}
