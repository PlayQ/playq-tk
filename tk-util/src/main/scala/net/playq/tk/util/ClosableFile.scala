package net.playq.tk.util

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.IO2

import java.io.File
import java.nio.file.{Files, Path}

/** File is deleted on `close` */
final class ClosableFile(val file: File) extends AutoCloseable {
  def path: Path = file.toPath

  def absolutePath: Path = file.toPath.toAbsolutePath

  override def close(): Unit = delete(file)

  private[this] def delete(f: java.io.File): Unit = {
    if (f.isDirectory) f.listFiles().foreach(delete)
    f.delete()
    ()
  }
}

object ClosableFile {
  def resourceFile[F[+_, +_]: IO2](path: Path): Lifecycle[F[Throwable, _], ClosableFile] = {
    Lifecycle.fromAutoCloseable(IO2(createFile(path)))
  }

  def resourceTempFile[F[+_, +_]: IO2](prefix: String = "managed_file", suffix: String = ".tmp"): Lifecycle[F[Throwable, _], ClosableFile] = {
    Lifecycle.fromAutoCloseable(IO2(createTempFile(prefix, suffix)))
  }

  def resourceTempDirectory[F[+_, +_]: IO2](prefix: String = "managed_dir"): Lifecycle[F[Throwable, _], ClosableFile] = {
    Lifecycle.fromAutoCloseable(IO2(createTempDirectory(prefix)))
  }

  def withDeleteOnClose[F[+_, +_]: IO2](file: File): Lifecycle[F[Throwable, _], ClosableFile] = {
    Lifecycle.fromAutoCloseable(IO2(new ClosableFile(file)))
  }

  def createFile(path: Path): ClosableFile = {
    new ClosableFile(Files.createFile(path).toFile)
  }

  def createTempFile(prefix: String = "managed_file", suffix: String = ".tmp"): ClosableFile = {
    new ClosableFile(Files.createTempFile(prefix, suffix).toFile)
  }

  def createTempDirectory(prefix: String = "managed_dir"): ClosableFile = {
    new ClosableFile(Files.createTempDirectory(prefix).toFile)
  }
}
