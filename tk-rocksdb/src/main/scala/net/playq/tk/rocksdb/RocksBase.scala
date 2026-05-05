package net.playq.tk.rocksdb

import distage.{ModuleDef, Tag, TagKK}
import izumi.distage.constructors.ClassConstructor
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.StandardAxis.Mode
import izumi.functional.bio.{BlockingIO2, F, IO2, Temporal2}
import logstage.LogIO2
import net.playq.tk.rocksdb.RocksBaseCodec.AnyRocksValue
import net.playq.tk.rocksdb.RocksBaseId.RocksBaseIdFile
import org.rocksdb.{FlushOptions, RocksDB}

import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable

trait RocksBase[F[_, _], RocksDBId] {
  def get[V: RocksBaseDecoder](key: AnyRocksValue): F[Throwable, Option[V]]
  def put(key: AnyRocksValue, value: AnyRocksValue): F[Throwable, Unit]
  def delete(key: AnyRocksValue): F[Throwable, Unit]

  def flush(force: Boolean): F[Throwable, Unit]

  def close(): F[Throwable, Unit]
}

object RocksBase {

  def rocksBaseModule[F[+_, +_]: TagKK, RocksDBId <: RocksBaseId: Tag: ClassConstructor]: ModuleDef = new ModuleDef {
    make[RocksDBId]
    make[RocksBaseIdFile[RocksDBId]].tagged(Mode.Prod).fromResource[RocksBaseIdFile.Prod[F, RocksDBId]]
    make[RocksBaseIdFile[RocksDBId]].tagged(Mode.Test).fromResource[RocksBaseIdFile.Test[F, RocksDBId]]

    make[RocksBase[F, RocksDBId]].fromResource {
      (_: RocksBaseFactory[F]).openBase(_: RocksBaseIdFile[RocksDBId])
    }
  }

  final class Impl[F[+_, +_]: IO2: BlockingIO2, RocksDBId] private (
    rocksDb: RocksDB,
    logger: LogIO2[F],
  ) extends RocksBase[F, RocksDBId] {

    private val closed: AtomicBoolean = new AtomicBoolean(false)
    private val semaphore: Semaphore  = new Semaphore(Int.MaxValue)

    override def flush(force: Boolean): F[Throwable, Unit] = {
      for {
        stats <- exec {
          db =>
            val opt = new FlushOptions().setWaitForFlush(true).setAllowWriteStall(force)
            try {
              db.flush(opt)
            } finally {
              opt.close()
            }
            db.getProperty("rocksdb.stats")
        }
        _ <- logger.debug(s"Rock base: $stats")
      } yield ()
    }

    override def put(key: AnyRocksValue, value: AnyRocksValue): F[Throwable, Unit] = {
      exec(_.put(key.codec.toBytes(key.value), value.codec.toBytes(value.value)))
    }

    override def get[V: RocksBaseDecoder](key: AnyRocksValue): F[Throwable, Option[V]] = {
      exec(db => Option(db.get(key.codec.toBytes(key.value))).map(RocksBaseCodec.fromBytes[V](_)))
    }

    override def delete(key: AnyRocksValue): F[Throwable, Unit] = {
      exec(_.delete(key.codec.toBytes(key.value)))
    }

    /** We should be sure that DB was not closed mid-action, nor before. */
    override def close(): F[Throwable, Unit] = synchronizeExitCalls(Int.MaxValue) {
      F.syncThrowable {
        if (!closed.get()) {
          rocksDb.close()
          closed.set(true)
        }
      }
    }

    /** We should be sure that DB was not closed mid action, nor before. */
    private def exec[A](action: RocksDB => A): F[Throwable, A] = synchronizeExitCalls(1) {
      F.ifThenElse(!closed.get())(
        F.syncThrowable(action(rocksDb)),
        F.fail(new IllegalStateException("Rocks DB database already closed.")),
      )
    }

    private def synchronizeExitCalls[E, A](permits: Int)(eff: => F[E, A]): F[E, A] = F.shiftBlocking {
      F.bracket(F.sync(semaphore.acquire(permits)))(_ => F.sync(semaphore.release(permits)))(_ => eff.uninterruptible)
    }

  }
  object Impl {

    def create[F[+_, +_]: IO2: BlockingIO2, RocksDBId](
      file: File,
      config: RocksBaseConfig,
      logger: LogIO2[F],
    ): Lifecycle[F[Throwable, _], RocksBase.Impl[F, RocksDBId]] = {
      for {
        options <- config.makeOptions[F]
        rocksDb <- Lifecycle.make {
          F.syncThrowable(RocksDB.open(options, file.getAbsolutePath))
            .map(new RocksBase.Impl[F, RocksDBId](_, logger))
        }(_.close())
      } yield rocksDb
    }

  }

  final class Dummy[F[+_, +_]: IO2, RocksDBId] extends RocksBase[F, RocksDBId] {

    private[this] val content = mutable.Map.empty[String, Array[Byte]]

    override def put(key: AnyRocksValue, value: AnyRocksValue): F[Throwable, Unit] = {
      F.sync {
        synchronized {
          content.addOne(key.codec.toString(key.value) -> value.codec.toBytes(value.value))
        }
      }.void
    }

    override def get[V: RocksBaseDecoder](key: AnyRocksValue): F[Throwable, Option[V]] = {
      F.sync {
        synchronized {
          content.get(key.codec.toString(key.value)).map(RocksBaseCodec.fromBytes[V])
        }
      }
    }

    override def delete(key: AnyRocksValue): F[Throwable, Unit] = {
      F.sync {
        synchronized {
          content.remove(key.codec.toString(key.value))
        }
      }.void
    }

    override def flush(force: Boolean): F[Throwable, Unit] = {
      F.unit
    }

    override def close(): F[Throwable, Unit] = {
      F.sync {
        synchronized {
          content.clear()
        }
      }
    }
  }

}
