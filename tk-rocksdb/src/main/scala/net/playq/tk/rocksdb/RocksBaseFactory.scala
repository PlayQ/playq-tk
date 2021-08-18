package net.playq.tk.rocksdb

import izumi.distage.model.definition.{Lifecycle, Lifecycle2}
import izumi.functional.bio.{F, IO2}
import org.rocksdb.RocksDB

import java.nio.file.Paths
import scala.collection.mutable

trait RocksBaseFactory[F[+_, +_]] {
  def mkBase[RocksDBId <: RocksBaseId](baseId: RocksDBId): Lifecycle2[F, Throwable, RocksBase[F, RocksDBId]]
}

object RocksBaseFactory {
  final class Dummy[F[+_, +_]: IO2] extends RocksBaseFactory[F] {
    private[this] val bases = mutable.HashMap.empty[RocksBaseId, RocksBase[F, _]]
    override def mkBase[RocksDBId <: RocksBaseId](baseId: RocksDBId): Lifecycle2[F, Throwable, RocksBase[F, RocksDBId]] = Lifecycle.liftF(F.sync {
      bases.getOrElseUpdate(baseId, new RocksBase.Dummy[F, RocksDBId]).asInstanceOf[RocksBase[F, RocksDBId]]
    })
  }

  final class Impl[F[+_, +_]: IO2](
    config: RocksDBConfig
  ) extends RocksBaseFactory[F] {
    override def mkBase[RocksDBId <: RocksBaseId](baseId: RocksDBId): Lifecycle2[F, Throwable, RocksBase[F, RocksDBId]] = Lifecycle.liftF(F.sync {
      new RocksBase[F, RocksDBId] {
        private[this] val base = Paths.get(baseId.base)
        private[this] val dbp  = base.resolve(baseId.dpb)
        base.toFile.mkdirs()
        private[this] val db = RocksDB.open(config.getOptions(), dbp.toString)

        override def put[K: RocksDBEncoder, V: RocksDBEncoder](key: K, value: V): F[Throwable, Unit] = {
          F.syncThrowable(db.put(RocksDBEncoder.toBytes(key), RocksDBEncoder.toBytes(value)))
        }

        override def get[K: RocksDBEncoder](key: K): F[Throwable, Array[Byte]] = {
          F.syncThrowable(db.get(RocksDBEncoder.toBytes(key)))
        }

        override def delete[K: RocksDBEncoder](key: K): F[Throwable, Unit] = {
          F.syncThrowable(db.delete(RocksDBEncoder.toBytes(key)))
        }
      }
    })
  }
}
