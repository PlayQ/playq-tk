package net.playq.tk.rocksdb

import izumi.distage.model.definition.{Lifecycle, Lifecycle2}
import izumi.functional.bio.{BlockingIO2, F, Fork2, IO2, Primitives2, Temporal2}
import logstage.LogIO2
import net.playq.tk.control.fiber.FiberEffect
import net.playq.tk.control.{Forever, InterruptSignal}
import net.playq.tk.rocksdb.RocksBaseId.RocksBaseIdFile
import net.playq.tk.util.ClosableFile

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

trait RocksBaseFactory[F[+_, +_]] {
  def openBase[RocksDBId <: RocksBaseId](file: RocksBaseIdFile[RocksDBId]): Lifecycle2[F, Throwable, RocksBase[F, RocksDBId]]
  def tmpBase(): Lifecycle2[F, Throwable, RocksBase[F, Nothing]]
}

object RocksBaseFactory {

  final class Dummy[F[+_, +_]: IO2](
    logger: LogIO2[F]
  ) extends RocksBaseFactory[F] {
    private[this] val bases = mutable.HashMap.empty[RocksBaseIdFile[?], RocksBase[F, ?]]

    override def openBase[RocksDBId <: RocksBaseId](file: RocksBaseIdFile[RocksDBId]): Lifecycle2[F, Throwable, RocksBase[F, RocksDBId]] = Lifecycle.liftF {
      logger.info(s"RocksBase: created a dummy database: ${file.file.getAbsolutePath -> "path"}.")
        *> F.sync {
          bases
            .getOrElseUpdate(file, new RocksBase.Dummy[F, RocksDBId])
            .asInstanceOf[RocksBase[F, RocksDBId]]
        }
    }

    override def tmpBase(): Lifecycle2[F, Throwable, RocksBase[F, Nothing]] = Lifecycle.liftF {
      F.sync(new RocksBase.Dummy[F, Nothing])
    }
  }

  final class Impl[F[+_, +_]: IO2: Fork2: Temporal2: BlockingIO2: Primitives2](
    config: RocksBaseConfig
  )(implicit logger: LogIO2[F]
  ) extends RocksBaseFactory[F] {

    override def openBase[RocksDBId <: RocksBaseId](id: RocksBaseIdFile[RocksDBId]): Lifecycle2[F, Throwable, RocksBase[F, RocksDBId]] = {
      for {
        base <- RocksBase.Impl.create[F, RocksDBId](id.file, config, logger)
        _    <- startFlusher(id.id, base, config.flushInterval)
        _    <- Lifecycle.liftF(logger.info(s"RocksBase: created database: ${id.file.getAbsolutePath -> "path"}."))
      } yield base
    }

    override def tmpBase(): Lifecycle2[F, Throwable, RocksBase[F, Nothing]] = {
      ClosableFile.resourceTempDirectory().flatMap(cf => RocksBase.Impl.create[F, Nothing](cf.file, config, logger))
    }

    private def startFlusher[RocksDBId <: RocksBaseId](
      id: RocksDBId,
      base: RocksBase[F, RocksDBId],
      interval: FiniteDuration,
    ): Lifecycle[F[Throwable, _], Unit] = new FiberEffect.Lifecycled[F, Unit] {

      override val fiberName: String = s"$id flusher"

      override def runInFiber(interruptSignal: InterruptSignal[F]): F[Nothing, Any] = {
        Forever.loopForeverWithSignal(fiberName, interruptSignal) {
          F.sleep(interval) *> base.flush(force = false)
        }
      }

      override def release(resource: FiberEffect.FiberEffectHandler[F]): F[Throwable, Unit] = {
        for {
          _ <- super.release(resource)
          _ <- base.flush(force = true)
        } yield ()
      }

      override def extract[B >: Unit](resource: FiberEffect.FiberEffectHandler[F]): Either[F[Throwable, B], B] = {
        Right(())
      }
    }
  }
}
