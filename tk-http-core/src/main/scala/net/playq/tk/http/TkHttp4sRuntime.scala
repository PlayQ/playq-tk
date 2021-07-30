package net.playq.tk.http

import izumi.distage.model.definition.Id
import izumi.functional.bio.{IO2, UnsafeRun2, Temporal2}
import izumi.idealingua.runtime.rpc.http4s._
import net.playq.tk.quantified.{ConcurrentEffect2, TimerThrowable}

import scala.concurrent.ExecutionContext

final class TkHttp4sRuntime[_BiIO[+_, +_]: IO2: Temporal2: UnsafeRun2: ConcurrentEffect2: TimerThrowable, Ctx, FullCtx, ClientId](
  blockingIOExecutionContext: ExecutionContext @Id("blockingIO")
) extends Http4sRuntime[_BiIO, Ctx, FullCtx, ClientId, Unit, Unit](blockingIOExecutionContext)
