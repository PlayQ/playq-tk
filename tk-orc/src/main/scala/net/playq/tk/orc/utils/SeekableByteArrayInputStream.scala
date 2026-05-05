package net.playq.tk.orc.utils

import org.apache.hadoop.fs.{PositionedReadable, Seekable}

import java.io.{ByteArrayInputStream, EOFException}

private[orc] final class SeekableByteArrayInputStream(buf: Array[Byte]) extends ByteArrayInputStream(buf) with Seekable with PositionedReadable {
  override def seek(pos: Long): Unit = {
    this.reset()
    this.skip(pos)
    ()
  }

  override def getPos: Long = pos.toLong

  override def seekToNewSource(targetPos: Long) = false

  override def read(position: Long, buffer: Array[Byte], offset: Int, length: Int): Int = {
    val oldPos = getPos
    var nread  = -1
    try {
      seek(position)
      nread = read(buffer, offset, length)
    } finally seek(oldPos)
    nread
  }

  override def readFully(position: Long, buffer: Array[Byte], offset: Int, length: Int): Unit = {
    var nread = 0
    while (nread < length) {
      val nbytes = read(position + nread, buffer, offset + nread, length - nread)
      if (nbytes < 0) throw new EOFException("End of file reached before reading fully.")
      nread += nbytes
    }
  }

  override def readFully(position: Long, buffer: Array[Byte]): Unit = {
    readFully(position, buffer, 0, buffer.length)
  }
}
