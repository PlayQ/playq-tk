package net.playq.tk.orc

import fs2.*
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.catz.*
import izumi.functional.bio.{F, IO2}
import net.playq.tk.orc.OrcValue.OrcAny
import net.playq.tk.orc.utils.MockingHDFS
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.orc.*

import java.io.File
import scala.collection.immutable.ArraySeq
import scala.collection.mutable

open class OrcReader[F[+_, +_]: IO2](
  fileReader: Reader,
  val schema: OrcSchema,
) {
  /** @param batchSize Minimum size of the batch to read in one call.
    *                  Set batchSize == readerBatchSize to call orc reader only once on each iteration,
    *                  otherwise reader will be called repeatedly while batch.size < batchSize
    * @param readerBatchSize Internal OrcReader VectorizedColumns size.
    *                        Recommended 1024 [[org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch.DEFAULT_SIZE]]
    */
  final def readAllWithHeader(
    batchSize: Int,
    readerBatchSize: Int           = 1024,
    filters: List[OrcRecordFilter] = Nil,
  )(implicit fs2c: Compiler.Target[F[Throwable, _]]
  ): F[Throwable, Vector[collection.Map[String, OrcAny]]] = {
    readStreamWithHeader(batchSize, readerBatchSize, filters).compile.toVector
  }

  /** @param batchSize Minimum size of the batch to read in one call.
    *                  Set batchSize == readerBatchSize to call orc reader only once on each iteration,
    *                  otherwise reader will be called repeatedly while batch.size < batchSize
    * @param readerBatchSize Internal OrcReader VectorizedColumns size.
    *                        Recommended 1024 [[org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch.DEFAULT_SIZE]]
    */
  def readStreamWithHeader(
    batchSize: Int,
    readerBatchSize: Int,
    filters: List[OrcRecordFilter] = Nil,
  ): Stream[F[Throwable, _], collection.Map[String, OrcAny]] = {
    readStream(batchSize, readerBatchSize, filters)
      .map(row => schema.columns.iterator.zipWithIndex.map { case (c, idx) => c.name -> row(idx) }.to(mutable.HashMap))
  }

  /** @param batchSize Minimum size of the batch to read in one call.
    *                  Set batchSize == readerBatchSize to call orc reader only once on each iteration,
    *                  otherwise reader will be called repeatedly while batch.size < batchSize
    * @param readerBatchSize Internal OrcReader VectorizedColumns size.
    *                        Recommended 1024 [[org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch.DEFAULT_SIZE]]
    */
  def readStream(
    batchSize: Int,
    readerBatchSize: Int           = 1024,
    filters: List[OrcRecordFilter] = Nil,
  ): Stream[F[Throwable, _], ArraySeq[OrcAny]] = {
    Stream.resource(iterator(readerBatchSize, filters).toCats).flatMap(_.stream(batchSize).unchunks)
  }

  /** Read without any error handling whole file.
    * @param readerBatchSize Internal OrcReader VectorizedColumns size.
    *                        Recommended 1024 [[org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch.DEFAULT_SIZE]]
    */
  def readUnsafe(readerBatchSize: Int = 1024, filters: List[OrcRecordFilter] = Nil): Seq[ArraySeq[OrcAny]] = {
    OrcRecordBatchIterator.create(fileReader, fileReader.getSchema, schema, filters, readerBatchSize) match {
      case Right(iter) =>
        try iter.flatten.toVector
        finally iter.close()
      case Left(err) =>
        throw err
    }
  }

  /** @param readerBatchSize Internal OrcReader VectorizedColumns size.
    *                        Recommended 1024 [[org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch.DEFAULT_SIZE]]
    */
  def iterator(readerBatchSize: Int, filters: List[OrcRecordFilter] = Nil): Lifecycle[F[Throwable, _], OrcRecordBatchIterator] = Lifecycle.fromAutoCloseable {
    F.fromEither {
      OrcRecordBatchIterator.create(fileReader, fileReader.getSchema, schema, filters, readerBatchSize)
    }
  }
}

object OrcReader {

  // configuration creation is pretty slow, so we will use singleton value
  private[orc] lazy val defaultConfiguration: Configuration = {
    val config = new Configuration()

    // todo: java 25 workaround
    config.set("fs.file.impl", classOf[org.apache.hadoop.fs.LocalFileSystem].getName)
    config.setBoolean("fs.file.impl.disable.cache", true)

    config
  }

  def openSchemaless[F[+_, +_]: IO2](bytes: Array[Byte]): Lifecycle[F[Throwable, _], OrcReader[F]] = {
    for {
      reader <- createReader(bytes)
      schema <- Lifecycle.liftF(F.fromEither(OrcSchema.fromOrcTypeDescriptor(reader.getSchema)))
    } yield new OrcReader(reader, schema)
  }

  def openSchemaless[F[+_, +_]: IO2](file: File): Lifecycle[F[Throwable, _], OrcReader[F]] = {
    for {
      reader <- createReader(new Path(file.getAbsolutePath))
      schema <- Lifecycle.liftF(F.fromEither(OrcSchema.fromOrcTypeDescriptor(reader.getSchema)))
    } yield new OrcReader(reader, schema)
  }

  def open[F[+_, +_]: IO2](file: File, schema: OrcSchema): Lifecycle[F[Throwable, _], OrcReader[F]] = {
    createReader(new Path(file.getAbsolutePath)).map(new OrcReader(_, schema))
  }

  def open[F[+_, +_]: IO2](bytes: Array[Byte], schema: OrcSchema): Lifecycle[F[Throwable, _], OrcReader[F]] = {
    createReader(bytes).map(new OrcReader(_, schema))
  }

  def streamFile[F[+_, +_]: IO2](
    file: File,
    schema: OrcSchema,
    batchSize: Int,
    readerBatchSize: Int           = 1024,
    filters: List[OrcRecordFilter] = Nil,
  ): Stream[F[Throwable, _], ArraySeq[OrcAny]] = {
    streamFromReader(open(file, schema))(_.readStream(batchSize, readerBatchSize, filters))
  }

  def streamFileWithHeader[F[+_, +_]: IO2](
    file: File,
    schema: OrcSchema,
    batchSize: Int,
    readerBatchSize: Int           = 1024,
    filters: List[OrcRecordFilter] = Nil,
  ): Stream[F[Throwable, _], collection.Map[String, OrcAny]] = {
    streamFromReader(open(file, schema))(_.readStreamWithHeader(batchSize, readerBatchSize, filters))
  }

  def stream[F[+_, +_]: IO2](
    bytes: Array[Byte],
    schema: OrcSchema,
    batchSize: Int,
    readerBatchSize: Int           = 1024,
    filters: List[OrcRecordFilter] = Nil,
  ): Stream[F[Throwable, _], ArraySeq[OrcAny]] = {
    streamFromReader(open(bytes, schema))(_.readStream(batchSize, readerBatchSize, filters))
  }

  def streamWithHeader[F[+_, +_]: IO2](
    bytes: Array[Byte],
    schema: OrcSchema,
    batchSize: Int,
    readerBatchSize: Int           = 1024,
    filters: List[OrcRecordFilter] = Nil,
  ): Stream[F[Throwable, _], collection.Map[String, OrcAny]] = {
    streamFromReader(open(bytes, schema))(_.readStreamWithHeader(batchSize, readerBatchSize, filters))
  }

  private def streamFromReader[F[+_, +_]: IO2, A](
    reader: Lifecycle[F[Throwable, _], OrcReader[F]]
  )(stream: OrcReader[F] => Stream[F[Throwable, _], A]
  ): Stream[F[Throwable, _], A] = {
    for {
      orcReader <- Stream.resource[F[Throwable, _], OrcReader[F]](reader.toCats)
      res       <- stream(orcReader)
    } yield res
  }

  private def createReader[F[+_, +_]: IO2](path: Path): Lifecycle[F[Throwable, _], Reader] = {
    Lifecycle.fromAutoCloseable {
      F.syncThrowable {
        OrcFile.createReader(path, OrcFile.readerOptions(defaultConfiguration))
      }
    }
  }

  private def createReader[F[+_, +_]: IO2](bytes: Array[Byte]): Lifecycle[F[Throwable, _], Reader] = {
    Lifecycle.liftF {
      F.syncThrowable {
        val conf    = new Configuration()
        val fs      = new MockingHDFS(conf, bytes)
        val pathStr = "/tmp/tmp.orc"
        OrcFile.createReader(new Path(pathStr), OrcFile.readerOptions(defaultConfiguration).filesystem(fs))
      }
    }
  }
}
