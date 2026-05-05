package net.playq.tk.parquet

import org.apache.parquet.io.{DelegatingSeekableInputStream, InputFile, SeekableInputStream}

import java.io.{ByteArrayInputStream, EOFException, IOException, RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.file.Path

/**
  * stolen from https://github.com/tideworks/arvo2parquet/blob/master/src/main/java/com/tideworks/data_load/io/InputFile.java
  */
@SuppressWarnings(Array("VarCouldBeVal"))
object ParquetNIOPathInputFile {
  private val COPY_BUFFER_SIZE = 8192

  def bytesToInputFile(bytes: Array[Byte]): InputFile = new InputFile {
    override def getLength: Long                = bytes.length.toLong
    override def newStream: SeekableInputStream = {
      val bis = new SeekableByteArrayInputStream(bytes)
      new DelegatingSeekableInputStream(bis) {
        override def getPos: Long             = bis.getPos.toLong
        override def seek(newPos: Long): Unit = bis.setPos(newPos.toInt)
      }
    }
  }

  def nioPathToInputFile(file: Path): InputFile = { // noinspection ConstantConditions
    assert(file != null)
    val input = new RandomAccessFile(file.toFile, "r")
    new InputFile() {
      override def getLength: Long = input.length

      override def newStream: SeekableInputStream = new SeekableInputStream() {
        final private val tmpBuf = new Array[Byte](COPY_BUFFER_SIZE)
        private var markPos      = 0

        override def read: Int = input.read

        override def read(b: Array[Byte]): Int = input.read(b)

        override def read(b: Array[Byte], off: Int, len: Int): Int = input.read(b, off, len)

        override def skip(n0: Long): Long = {
          var n       = n0
          val savPos  = input.getFilePointer
          val amtLeft = input.length - savPos
          n = Math.min(n, amtLeft)
          val newPos = savPos + n
          input.seek(newPos)
          val curPos = input.getFilePointer
          curPos - savPos
        }

        override def available = 0

        override def close(): Unit = {
          input.close()
        }

        override def mark(readlimit: Int): Unit = {
          try
            markPos = input.getFilePointer.toInt
          catch {
            case e: IOException => throw e
          }
        }

        override def reset(): Unit = {
          input.seek(markPos.toLong)
        }

        override def markSupported = true

        override def getPos: Long = input.getFilePointer

        override def seek(l: Long): Unit = {
          input.seek(l)
        }

        override def readFully(bytes: Array[Byte]): Unit = {
          input.readFully(bytes)
        }

        override def readFully(bytes: Array[Byte], i: Int, i1: Int): Unit = {
          input.readFully(bytes, i, i1)
        }

        override def read(byteBuffer: ByteBuffer): Int =
          readDirectBuffer(byteBuffer, tmpBuf, input.read)

        override def readFully(byteBuffer: ByteBuffer): Unit = {
          readFullyDirectBuffer(byteBuffer, tmpBuf, input.read)
        }
      }
    }
  }

  private trait ByteBufReader {
    def read(b: Array[Byte], off: Int, len: Int): Int
  }

  private def readDirectBuffer(
    byteBufr: ByteBuffer,
    tmpBuf: Array[Byte],
    rdr: ParquetNIOPathInputFile.ByteBufReader,
  ): Int = { // copy all the bytes that return immediately, stopping at the first
    // read that doesn't return a full buffer.
    var nextReadLength = Math.min(byteBufr.remaining, tmpBuf.length)
    var totalBytesRead = 0
    var bytesRead      = 0
    while ({
      {
        val i = rdr.read(tmpBuf, 0, nextReadLength)
        bytesRead = i
        i
      } == tmpBuf.length
    }) {
      byteBufr.put(tmpBuf)
      totalBytesRead += bytesRead
      nextReadLength = Math.min(byteBufr.remaining, tmpBuf.length)
    }
    if (bytesRead < 0) { // return -1 if nothing was read
      if (totalBytesRead == 0) -1
      else totalBytesRead
    } else { // copy the last partial buffer
      byteBufr.put(tmpBuf, 0, bytesRead)
      totalBytesRead += bytesRead
      totalBytesRead
    }
  }

  private def readFullyDirectBuffer(byteBufr: ByteBuffer, tmpBuf: Array[Byte], rdr: ParquetNIOPathInputFile.ByteBufReader): Unit = {
    var nextReadLength = Math.min(byteBufr.remaining, tmpBuf.length)
    var bytesRead      = 0
    while ({
      nextReadLength > 0 && ({
        val i = rdr.read(tmpBuf, 0, nextReadLength)
        bytesRead = i
        i
      } >= 0)
    }) {
      byteBufr.put(tmpBuf, 0, bytesRead)
      nextReadLength = Math.min(byteBufr.remaining, tmpBuf.length)
    }
    if (bytesRead < 0 && byteBufr.remaining > 0) throw new EOFException("Reached the end of stream with " + byteBufr.remaining + " bytes left to read")
  }

  private class SeekableByteArrayInputStream(buf: Array[Byte]) extends ByteArrayInputStream(buf) {
    def setPos(pos: Int): Unit = this.pos = pos
    def getPos: Int            = this.pos
  }
}
