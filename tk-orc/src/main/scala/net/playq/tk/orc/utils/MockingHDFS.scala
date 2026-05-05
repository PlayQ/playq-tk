package net.playq.tk.orc.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.*
import org.apache.hadoop.fs.permission.FsPermission
import org.apache.hadoop.util.Progressable

import java.io.IOException
import java.net.URI

private[orc] final class MockingHDFS(conf: Configuration, bytes: Array[Byte]) extends FileSystem {
  setConf(conf)
  override def getUri: URI                                 = new URI("mock:///")
  override def open(path: Path, i: Int): FSDataInputStream = new FSDataInputStream(new SeekableByteArrayInputStream(bytes))
  override def getFileStatus(path: Path): FileStatus       = new FileStatus(bytes.length.toLong, false, 1, 4096, 0, path)
  override def create(path: Path, fsp: FsPermission, b: Boolean, i: Int, i1: Short, l: Long, p: Progressable): FSDataOutputStream = throw new IOException("Can't create")
  override def append(path: Path, i: Int, p: Progressable): FSDataOutputStream                                                    = throw new IOException("Can't append")
  override def rename(path: Path, path1: Path): Boolean                                                                           = false
  override def delete(path: Path, b: Boolean): Boolean                                                                            = false
  override def listStatus(path: Path): Array[FileStatus]                                                                          = new Array[FileStatus](0)
  override def setWorkingDirectory(path: Path): Unit                                                                              = ()
  override def getWorkingDirectory: Path                                                                                          = new Path("/")
  override def mkdirs(path: Path, fsPermission: FsPermission): Boolean                                                            = false
}
