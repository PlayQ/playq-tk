package net.playq.tk.control.fiber

import distage.Lifecycle
import izumi.functional.bio.{BlockingIO2, Error2, F, Fiber2, Fork2, Monad2, Primitives2, Promise2, Temporal2}
import logstage.LogIO2
import net.playq.tk.control.InterruptSignal
import net.playq.tk.control.fiber.FiberEffect.{FiberEffectHandler, Lifecycled}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait FiberEffect[F[+_, +_]] {
  self =>
  val fiberName: String
  def runInFiber(interruptSignal: InterruptSignal[F]): F[Nothing, Any]
  def runWithHandler(implicit FK: Fork2[F], blockingIO: BlockingIO2[F], P: Primitives2[F], M: Error2[F]): F[Nothing, FiberEffectHandler[F]] = {
    for {
      signal  <- InterruptSignal.make[F]
      promise <- F.mkPromise[Nothing, Unit]
      fiber   <- blockingIO.shiftBlocking(runInFiber(signal).guarantee(promise.succeed(()).void)).fork
    } yield FiberEffectHandler(signal, fiber, promise)
  }
  final def lifecycle(implicit E: Error2[F], F: Fork2[F], P: Primitives2[F], B: BlockingIO2[F], T: Temporal2[F], L: LogIO2[F]): Lifecycle[F[Throwable, _], Unit] = {
    new Lifecycled[F, Unit] {
      override def extract[B >: Unit](resource: FiberEffectHandler[F]): Either[F[Throwable, B], B] = Right(())
      override val fiberName: String                                                               = self.fiberName
      override def runInFiber(interruptSignal: InterruptSignal[F]): F[Nothing, Any]                = self.runInFiber(interruptSignal)
    }
  }
}

object FiberEffect {
  final case class FiberEffectHandler[F[+_, +_]](
    signal: InterruptSignal[F],
    fiber: Fiber2[F, Nothing, Any],
    promise: Promise2[F, Nothing, Unit],
  ) {
    def close(await: Option[FiniteDuration] = Some(1.seconds))(implicit T: Temporal2[F], M: Monad2[F]): F[Nothing, Unit] = {
      for {
        _                  <- signal.interrupt
        finishedGracefully <- await match {
          case Some(t) => promise.await.timeout(t).map(_.nonEmpty)
          case None    => F.pure(false)
        }
        _ <- F.when(!finishedGracefully)(fiber.interrupt.void)
      } yield ()
    }
  }

  abstract class Lifecycled[F[+_, +_]: Fork2: LogIO2: Error2: Temporal2: BlockingIO2: Primitives2, A] extends Lifecycle[F[Throwable, _], A] with FiberEffect[F] {
    override type InnerResource = FiberEffectHandler[F]
    override def acquire: F[Throwable, FiberEffectHandler[F]] = {
      LogIO2[F].info(s"Starting fiber $fiberName ...") *> runWithHandler
    }
    override def release(resource: FiberEffectHandler[F]): F[Throwable, Unit] = {
      LogIO2[F].info(s"Interrupting fiber $fiberName ...") *> resource.close()
    }
  }

  abstract class LifecycledSelf[F[+_, +_]: Fork2: LogIO2: Error2: Temporal2: BlockingIO2: Primitives2, A] extends Lifecycled[F, A] {
    self: A =>
    override def extract[B >: A](resource: FiberEffectHandler[F]): Either[F[Throwable, B], B] = Right(self)
  }
}
