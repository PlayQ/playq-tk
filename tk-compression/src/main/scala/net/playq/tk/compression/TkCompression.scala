package net.playq.tk.compression

import com.github.luben.zstd.Zstd
import izumi.functional.bio.{F, IO2}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

import java.io.*
import java.nio.file.{Files, Path}
import java.util.zip.*
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.chaining.scalaUtilChainingOps

trait TkCompression[F[+_, +_]] {
  // gzip
  def inflate(bytes: Array[Byte]): F[Throwable, Array[Byte]]
  def deflate(bytes: Array[Byte]): F[Throwable, Array[Byte]]
  def inflate(file: File, target: Path): F[Throwable, Unit]
  def deflate(file: File, target: Path): F[Throwable, Unit]

  // zip
  def zip(dir: File, zip: File): F[Throwable, Unit]
  def zip(file: File, fileName: String, zip: File): F[Throwable, Unit]
  def unzip(zip: File, dir: File): F[Throwable, Unit]

  // tar.gz
  def extractTarArchive(
    archiveFile: Path,
    outputDir: Path,
    expectedSuffix: Option[String],
    mapEntryPath: String => String = identity,
  ): F[Throwable, List[File]]
  def extractFileFromTarArchive(archiveFile: Path, outputDir: Path, expectedSuffix: String): F[Throwable, Option[File]]

  // zstd
  def zstd(bytes: Array[Byte], compressionLevel: Int): F[Throwable, Array[Byte]]
  def unzstd(bytes: Array[Byte]): F[Throwable, Array[Byte]]
}

object TkCompression {

  abstract class Base[F[+_, +_]: IO2] extends TkCompression[F] {

    override def zip(dir: File, zip: File): F[Throwable, Unit] = {
      F.syncThrowable(new FileOutputStream(zip)).bracketAuto {
        fos =>
          F.syncThrowable(new ZipOutputStream(fos)).bracketAuto {
            zos =>
              F.traverse_(dir.listFiles()) {
                dirFile =>
                  F.syncThrowable(new FileInputStream(dirFile)).bracketAuto {
                    fileInputStream =>
                      for {
                        _ <- F.syncThrowable(zos.putNextEntry(new ZipEntry(dirFile.getName)))
                        _ <- readStream(fileInputStream, zos)
                        _ <- F.syncThrowable(zos.closeEntry())
                      } yield ()
                  }
              }.guarantee(F.sync(zos.finish()))
          }
      }
    }

    override def zip(file: File, fileName: String, zip: File): F[Throwable, Unit] = {
      F.syncThrowable(new FileOutputStream(zip)).bracketAuto {
        fos =>
          F.syncThrowable(new ZipOutputStream(fos)).bracketAuto {
            zos =>
              F.syncThrowable(new FileInputStream(file)).bracketAuto {
                fileInputStream =>
                  for {
                    _ <- F.syncThrowable(zos.putNextEntry(new ZipEntry(fileName)))
                    _ <- readStream(fileInputStream, zos)
                    _ <- F.syncThrowable(zos.closeEntry())
                  } yield ()
              }.guarantee(F.sync(zos.finish()))
          }
      }
    }

    override def unzip(zip: File, dir: File): F[Throwable, Unit] = {
      F.syncThrowable(new ZipFile(zip)).bracketAuto {
        zipFile =>
          F.traverse_(zipFile.entries().asIterator().asScala.toList) {
            zipEntry =>
              F.when(!zipEntry.isDirectory) {
                F.syncThrowable(zipFile.getInputStream(zipEntry)).bracketAuto {
                  zipInputStream =>
                    for {
                      outputFile <- F.syncThrowable {
                        val outputFile = dir.toPath.resolve(zipEntry.getName)
                        val parent     = outputFile.getParent
                        if (parent != null) parent.toFile.mkdirs()
                        outputFile.toFile
                      }
                      _ <- F.syncThrowable(new FileOutputStream(outputFile)).bracketAuto {
                        fileOutputStream =>
                          readStream(zipInputStream, fileOutputStream)
                      }
                    } yield ()
                }
              }
          }
      }
    }

    def extractTarArchive(
      archiveFile: Path,
      outputDir: Path,
      expectedSuffix: Option[String],
      mapEntryPath: String => String = identity,
    ): F[Throwable, List[File]] = {
      tarArchiveBracket(archiveFile) {
        tarInputStream =>
          F.iterateWhile(
            for {
              maybeTarEntry <- F.sync(Option(tarInputStream.getNextEntry))
              withSuffix     = expectedSuffix match {
                case Some(suffix) => maybeTarEntry.filter(_.getName.endsWith(suffix))
                case None         => maybeTarEntry
              }
              _ <- F.traverse(withSuffix) {
                tarEntry =>
                  extractTarEntry(tarInputStream, tarEntry, outputDir, mapEntryPath)
              }
            } yield maybeTarEntry
          ) {
            case None => false
            case _    => true
          }.void
      } *> F.sync(Files.walk(outputDir).iterator().asScala.map(_.toFile).filter(_.isFile).toList)
    }

    def extractFileFromTarArchive(archiveFile: Path, outputDir: Path, expectedSuffix: String): F[Throwable, Option[File]] = {
      tarArchiveBracket(archiveFile) {
        tarInputStream =>
          for {
            maybeTarEntry <- F.iterateWhile(F.sync(tarInputStream.getNextEntry)) {
              case null                                                                     => false
              case tarEntry if tarEntry.getName.endsWith(expectedSuffix) && tarEntry.isFile => false
              case _                                                                        => true
            }
            res <- F.traverse(Option(maybeTarEntry)) {
              tarEntry =>
                extractTarEntry(tarInputStream, tarEntry, outputDir)
            }
          } yield res
      }
    }

    private def extractTarEntry(
      tarInputStream: TarArchiveInputStream,
      tarEntry: TarArchiveEntry,
      outputDir: Path,
      mapEntryPath: String => String = identity,
    ): F[Throwable, File] = {
      val destPath = mapEntryPath(tarEntry.getName)
      for {
        destFile <- F.syncThrowable {
          new File(outputDir.toFile, destPath)
            .tap(f => Option(f.getParentFile).foreach(_.mkdirs()))
            .tap(_.createNewFile())
        }
        _ <- F.syncThrowable(new FileOutputStream(destFile)).bracketAuto {
          fout =>
            F.syncThrowable(new BufferedOutputStream(fout)).bracketAuto {
              bout =>
                readStream(tarInputStream, bout)
            }
        }
      } yield destFile
    }

    protected def readStream(inputStream: InputStream, outStream: OutputStream): F[Throwable, Unit] = F.syncThrowable {
      val btoRead = Array.ofDim[Byte](1024)
      var len     = inputStream.read(btoRead, 0, 1024)
      while (len != -1) {
        outStream.write(btoRead, 0, len)
        len = inputStream.read(btoRead, 0, 1024)
      }
    }

    private[this] def tarArchiveBracket[A](archiveFile: Path)(f: TarArchiveInputStream => F[Throwable, A]): F[Throwable, A] = {
      F.syncThrowable(new FileInputStream(archiveFile.toFile)).bracketAuto {
        fis =>
          F.syncThrowable(new BufferedInputStream(fis)).bracketAuto {
            bis =>
              F.syncThrowable(new GzipCompressorInputStream(bis)).bracketAuto {
                gzis =>
                  F.syncThrowable(new TarArchiveInputStream(gzis)).bracketAuto(f)
              }
          }
      }
    }
  }

  abstract class Gzip[F[+_, +_]: IO2] extends TkCompression.Base[F] {

    override def deflate(bytes: Array[Byte]): F[Throwable, Array[Byte]] = {
      F.syncThrowable(new ByteArrayOutputStream()).bracketAuto {
        aos =>
          F.syncThrowable(new GZIPOutputStream(aos)).bracketAuto {
            gzos =>
              F.syncThrowable {
                gzos.write(bytes)
              }
          }
            *> F.syncThrowable(aos.toByteArray)
      }
    }

    override def inflate(bytes: Array[Byte]): F[Throwable, Array[Byte]] = {
      F.syncThrowable(new ByteArrayInputStream(bytes)).bracketAuto {
        ais =>
          F.syncThrowable(new GZIPInputStream(ais)).bracketAuto {
            gzis =>
              F.syncThrowable(gzis.readAllBytes())
          }
      }
    }

    override def deflate(file: File, target: Path): F[Throwable, Unit] = {
      F.syncThrowable(new FileOutputStream(target.toFile)).bracketAuto {
        fos =>
          F.syncThrowable(new GZIPOutputStream(fos)).bracketAuto {
            gzos =>
              F.syncThrowable(new FileInputStream(file)).bracketAuto {
                fis =>
                  readStream(fis, gzos)
              }
          }
      }
    }

    override def inflate(file: File, target: Path): F[Throwable, Unit] = {
      F.syncThrowable(new FileInputStream(file)).bracketAuto {
        fis =>
          F.syncThrowable(new GZIPInputStream(fis)).bracketAuto {
            gzis =>
              F.syncThrowable(new FileOutputStream(target.toFile)).bracketAuto {
                fos =>
                  readStream(gzis, fos)
              }
          }
      }
    }
  }

  final class WithZstd[F[+_, +_]: IO2] extends Gzip[F] {

    override def zstd(bytes: Array[Byte], compressionLevel: Int): F[Throwable, Array[Byte]] = {
      F.syncThrowable(Zstd.compress(bytes, compressionLevel))
    }

    override def unzstd(bytes: Array[Byte]): F[Throwable, Array[Byte]] = {
      F.syncThrowable(Zstd.decompress(bytes, Zstd.getFrameContentSize(bytes).toInt))
    }
  }

}
