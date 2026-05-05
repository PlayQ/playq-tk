package net.playq.tk.parquet

import org.apache.parquet.io.{OutputFile, PositionOutputStream}

import java.io.OutputStream

/**
  * stolen from https://github.com/tideworks/arvo2parquet/blob/master/src/main/java/com/tideworks/data_load/io/OutputFile.java
  */
final class ParquetOutputStreamOutputFile(output: OutputStream) extends OutputFile {
  override def create(blockSizeHint: Long): PositionOutputStream =
    makePositionOutputStream()

  override def createOrOverwrite(blockSizeHint: Long): PositionOutputStream =
    makePositionOutputStream()

  override def supportsBlockSize: Boolean = false

  override def defaultBlockSize: Long = 0

  private[this] def makePositionOutputStream(): PositionOutputStream = {
    new PositionOutputStream() {
      private[this] var position = 0L

      override def write(b: Int): Unit = {
        output.write(b)
        position += 1
      }

      override def write(b: Array[Byte]): Unit = {
        output.write(b)
        position += b.length
      }

      override def write(b: Array[Byte], off: Int, len: Int): Unit = {
        output.write(b, off, len)
        position += len
      }

      override def flush(): Unit = {
        output.flush()
      }

      override def close(): Unit = {
        output.close()
      }

      override def getPos: Long = position
    }
  }
}
