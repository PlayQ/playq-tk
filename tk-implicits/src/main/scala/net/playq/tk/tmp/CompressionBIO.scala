package net.playq.tk.tmp

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}

import izumi.functional.bio.{F, IO2}

trait CompressionIO2[F[+_, +_]] {
  def deflate(bytes: Array[Byte]): F[Throwable, Array[Byte]]
  def inflate(bytes: Array[Byte]): F[Throwable, Array[Byte]]
}

final class CompressionIO2Gzip[F[+_, +_]: IO2] extends CompressionIO2[F] {

  def deflate(bytes: Array[Byte]): F[Throwable, Array[Byte]] = {
    for {
      aos <- F.syncThrowable(new ByteArrayOutputStream())
      _ <- F.bracket(F.syncThrowable(new GZIPOutputStream(aos)))(gzos => F.sync(gzos.close())) {
        gzos =>
          F.syncThrowable(gzos.write(bytes))
      }
    } yield {
      aos.toByteArray
    }
  }

  def inflate(bytes: Array[Byte]): F[Throwable, Array[Byte]] = {
    for {
      ais <- F.syncThrowable(new ByteArrayInputStream(bytes))
      bytes <- F.bracket(F.syncThrowable(new GZIPInputStream(ais)))(gzis => F.sync(gzis.close())) {
        gzis =>
          F.syncThrowable(gzis.readAllBytes())
      }
    } yield {
      bytes
    }
  }
}
