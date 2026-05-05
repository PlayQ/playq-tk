package net.playq.tk.control.fiber

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, BlockingIO2, F, Fork2, Primitives2, Temporal2}
import izumi.fundamentals.platform.strings.IzString.*
import logstage.LogIO2
import net.playq.tk.control.fiber.FiberEffect.FiberEffectHandler
import net.playq.tk.control.fiber.FiberEffectRunner.FiberEffects

final class FiberEffectRunner[F[+_, +_]: Async2: Temporal2: Fork2: Primitives2: BlockingIO2](
  effects: Set[FiberEffect[F]],
  log: LogIO2[F],
) extends Lifecycle.Basic[F[Throwable, _], FiberEffects[F]] {
  override def acquire: F[Throwable, FiberEffects[F]] = {
    for {
      _      <- log.info(s"Going to start ${effects.map(_.fiberName).toList.sorted.niceList() -> "fibers"}")
      fibers <- F.traverse(effects)(_.runWithHandler)
    } yield FiberEffects(fibers)
  }

  override def release(resource: FiberEffects[F]): F[Throwable, Unit] = {
    for {
      _ <- log.info(s"Stopping ${resource.handlers.size -> "fibers"} out of ${effects.size -> "initial"}...")
      _ <- F.parTraverse_(resource.handlers)(_.close())
    } yield ()
  }
}

object FiberEffectRunner {
  final case class FiberEffects[F[+_, +_]](handlers: List[FiberEffectHandler[F]])
}
