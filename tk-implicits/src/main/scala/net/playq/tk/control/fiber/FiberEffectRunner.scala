package net.playq.tk.control.fiber

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Applicative2, BlockingIO2, F, Fiber2, Fork2}
import izumi.fundamentals.platform.strings.IzString.*
import logstage.LogIO2
import FiberEffectRunner.FiberEffects
import net.playq.tk.control.Forever

final class FiberEffectRunner[F[+_, +_]: Applicative2: Fork2](
  effects: Set[FiberEffect[F]],
  log: LogIO2[F],
  blockingIO: BlockingIO2[F],
) extends Lifecycle.Basic[F[Throwable, _], FiberEffects[F]] {
  override def acquire: F[Throwable, FiberEffects[F]] = {
    log.info(s"Going to start ${effects.map(_.fiberName).toList.sorted.niceList() -> "fibers"}") *>
    F.traverse(effects)(p => blockingIO.shiftBlocking(p.runInFiber()).fork).map(FiberEffects(_))
  }

  override def release(resource: FiberEffects[F]): F[Throwable, Unit] = {
    log.info("Stopping fibers ...") *>
    F.traverse_(resource.fibers)(_.interrupt.void)
  }
}

object FiberEffectRunner {
  final case class FiberEffects[F[+_, +_]](fibers: List[Fiber2[F, Nothing, Forever]])
}
