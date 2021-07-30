package net.playq.tk.concurrent

import izumi.distage.model.definition.Id
import net.playq.tk.concurrent.threadpools.MeteredExecutor
import zio.blocking.Blocking
import zio.internal.Executor

final class ZIOBlocking(
  meteredExecutor: MeteredExecutor @Id("blockingIO")
) extends Blocking.Service {
  override def blockingExecutor: Executor = meteredExecutor.executor
}
