package net.playq.tk.rocksdb

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2}
import net.playq.tk.util.ClosableFile

import java.io.File

abstract class RocksBaseId(
  val path: String,
  val transient: Boolean,
) {
  override def toString: String = s"rocksdb:${if (transient) "<tmp>" else ""}:$path"
}

object RocksBaseId {

  final case class RocksBaseIdFile[T](
    id: T,
    file: File,
  )
  object RocksBaseIdFile {

    final class Prod[F[+_, +_]: IO2, Id <: RocksBaseId](
      id: Id,
      config: RocksBaseConfig,
    ) extends Lifecycle.Of[F[Throwable, _], RocksBaseIdFile[Id]](
        for {
          newDir <- Lifecycle.liftF {
            F.syncThrowable {
              val baseDir     = new File(config.defaultBaseDir)
              val presumedDir = baseDir.toPath.resolve(id.path).toFile
              val parent      = presumedDir.getParentFile
              if (parent != null && !parent.exists()) {
                parent.mkdirs()
              }
              presumedDir
            }
          }
          res <-
            if (id.transient) {
              ClosableFile.withDeleteOnClose(newDir).map(cf => RocksBaseIdFile[Id](id, cf.file))
            } else {
              Lifecycle.pure[F[Throwable, _]](RocksBaseIdFile[Id](id, newDir))
            }
        } yield res
      )

    final class Test[F[+_, +_]: IO2, Id <: RocksBaseId](
      id: Id
    ) extends Lifecycle.Of[F[Throwable, _], RocksBaseIdFile[Id]](
        ClosableFile.resourceTempDirectory().map(c => RocksBaseIdFile[Id](id, c.file))
      )

  }

}
