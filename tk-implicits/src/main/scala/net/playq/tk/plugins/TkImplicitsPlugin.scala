package net.playq.tk.plugins

import cats.effect._
import distage.plugins.PluginDef
import izumi.distage.framework.services.ResourceRewriter
import izumi.distage.model.definition.{Id, ModuleDef}
import izumi.functional.bio.UnsafeRun2.ZIORunner
import izumi.functional.bio.{Clock2, _}
import izumi.functional.mono.{Clock, Entropy}
import izumi.fundamentals.platform.functional.{Identity, Identity2}
import izumi.logstage.api.IzLogger
import logstage._
import net.playq.tk.concurrent.threadpools.{MeteredExecutor, NamingThreadFactory, ThreadPools}
import net.playq.tk.concurrent.{FiberWatchdog, LoggingZioRunner, YieldOpCounts, ZIOBlocking}
import net.playq.tk.implicits.Identity2Instances._
import zio.blocking.Blocking
import zio.internal.Platform
import zio.internal.tracing.TracingConfig
import zio.{Has, IO, Runtime}

import java.util.concurrent.{ScheduledExecutorService, ThreadFactory, ThreadPoolExecutor}
import scala.concurrent.ExecutionContext

object TkImplicitsPlugin extends PluginDef with IO2ImplicitsModule with ZIORuntimeModule

private[plugins] trait IO2ImplicitsModule extends ModuleDef {
  make[LogIO2[IO]]
    .from(LogZIO.withFiberId(_: IzLogger))
    .aliased[UnsafeLogIO2[IO]]
    .aliased[LogCreateIO2[IO]]
  make[LogIO[IO[Throwable, ?]]].from((_: LogIO2[IO]).widen[IO[Throwable, ?]])

  make[FiberWatchdog[IO]].from[FiberWatchdog.Impl]

  addImplicit[Applicative2[Identity2]]

  make[Clock[Identity]].fromValue(Clock.Standard)
  make[Entropy[Identity]].fromValue(Entropy.Standard)
  make[Clock2[IO]].from {
    (impureClock: Clock[Identity], syncSafe: SyncSafe2[IO]) =>
      Clock.fromImpure[IO[Nothing, ?]](impureClock)(syncSafe)
  }
  make[Entropy2[IO]].from {
    (impureEntropy: Entropy[Identity], syncSafe: SyncSafe2[IO]) =>
      Entropy.fromImpure[IO[Nothing, ?]](impureEntropy)(syncSafe)
  }
}

private[plugins] trait ZIORuntimeModule extends ModuleDef {

  make[ZIORunner].from {
    (cpuPool: ThreadPoolExecutor @Id("zio"), log: IzLogger, tracingConfig: TracingConfig, yieldOpCounts: YieldOpCounts) =>
      LoggingZioRunner(cpuPool, log, tracingConfig, yieldOpCounts)
  }
  make[UnsafeRun2[IO]].using[ZIORunner]

  make[Platform].from((_: ZIORunner).platform)
  make[Runtime[Any]].from((_: ZIORunner).runtime)
  make[TracingConfig].from(TracingConfig.enabled)

  make[YieldOpCounts].fromValue(YieldOpCounts)

  // Blocking
  make[Blocker].from {
    Blocker.liftExecutionContext(_: ExecutionContext @Id("blockingIO"))
  }

  make[Blocking.Service].from[ZIOBlocking]
  make[Blocking].from(Has(_: Blocking.Service))

  // Executor Metrics
  make[MeteredExecutor].named("zio").from {
    (threadPool: ThreadPoolExecutor @Id("zio"), yieldOpCounts: YieldOpCounts) =>
      MeteredExecutor(threadPool, yieldOpCount = yieldOpCounts.zioYieldOpCount)
  }
  make[MeteredExecutor].named("blockingIO").from {
    (threadPool: ThreadPoolExecutor @Id("blockingIO"), yieldOpCounts: YieldOpCounts) =>
      MeteredExecutor(threadPool, yieldOpCount = yieldOpCounts.blockingYieldOpCount)
  }
  ///

  //
  make[ThreadFactory].named("zio").from {
    new NamingThreadFactory("zio")
  }
  make[ThreadFactory].named("zioTimer").from {
    new NamingThreadFactory("zioTimer")
  }
  make[ThreadFactory].named("blockingIO").from {
    new NamingThreadFactory("blockingIO")
  }
  ///

  // CPU tasks pool
  make[ThreadPoolExecutor].named("zio").fromResource {
    (f: ThreadFactory @Id("zio"), logger: IzLogger) =>
      ResourceRewriter.fromExecutorService(logger, ThreadPools.zio(f))
  }

  // Blocking IO pool
  make[ThreadPoolExecutor].named("blockingIO").fromResource {
    (f: ThreadFactory @Id("blockingIO"), logger: IzLogger) =>
      ResourceRewriter.fromExecutorService(logger, ThreadPools.blocking(f))
  }

  // Sleep pool
  make[ScheduledExecutorService].named("zioTimer").fromResource {
    (f: ThreadFactory @Id("zioTimer"), logger: IzLogger) =>
      ResourceRewriter.fromExecutorService(logger, ThreadPools.zioTimer(f))
  }
  //

  // Execution contexts
  make[ExecutionContext].named("blockingIO").from {
    es: ThreadPoolExecutor @Id("blockingIO") =>
      ExecutionContext.fromExecutorService(es): ExecutionContext // prevent `ResourceRewriter` rewrite to ExecutorService Resource
  }
  make[ExecutionContext].named("zio").from {
    es: ThreadPoolExecutor @Id("zio") =>
      ExecutionContext.fromExecutorService(es): ExecutionContext // prevent `ResourceRewriter` rewrite to ExecutorService Resource
  }
  make[ExecutionContext].named("global").from(scala.concurrent.ExecutionContext.global)
  //
}
