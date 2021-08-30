package net.playq.tk.util

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.IO2

import java.io.File
import java.nio.file.{Files, Path}
import scala.util.chaining.*

sealed trait ManagedFile extends AutoCloseable {
  val file: File
  final def path: Path         = file.toPath
  final def absolutePath: Path = file.toPath.toAbsolutePath
}
object ManagedFile {
  def managedFile[F[+_, +_]: IO2](prefix: String = "managed_file", suffix: String = ".tmp"): Lifecycle[F[Throwable, _], ManagedFile] = {
    Lifecycle.fromAutoCloseable(IO2(createFile(prefix, suffix)))
  }
  def managedDirectory[F[+_, +_]: IO2](prefix: String = "managed_dir"): Lifecycle[F[Throwable, _], ManagedFile] = {
    Lifecycle.fromAutoCloseable(IO2(createDirectory(prefix)))
  }

  def createFile(prefix: String = "managed_file", suffix: String = ".tmp"): ManagedFile = {
    new Managed(Files.createTempFile(prefix, suffix).toFile)
  }
  def createDirectory(prefix: String = "managed_dir"): ManagedFile = {
    new Managed(Files.createTempDirectory(prefix).toFile)
  }

  def managedExternal(path: Path): ManagedFile = new Managed(path.toFile)

  def external(file: File): ManagedFile = new External(file)
  def external(path: Path): ManagedFile = new External(path.toFile)
  def external(prefix: String = "managed_file", suffix: String = ".tmp"): ManagedFile = {
    new External(
      Files.createTempFile(prefix, suffix).toFile.tap(_.deleteOnExit())
    )
  }

  private final class External(val file: File) extends ManagedFile {
    override def close(): Unit = ()
  }
  private final class Managed private[ManagedFile] (val file: File) extends ManagedFile {
    override def close(): Unit = delete(file)

    private[this] def delete(f: java.io.File): Unit = {
      if (f.isDirectory) f.listFiles().foreach(delete)
      f.delete()
      ()
    }
  }
}
