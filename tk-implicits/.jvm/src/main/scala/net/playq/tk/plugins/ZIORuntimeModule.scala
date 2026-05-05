package net.playq.tk.plugins

import izumi.distage.framework.services.ResourceRewriter
import izumi.distage.model.definition.{ModuleDef, Id}
import izumi.functional.bio._
import izumi.functional.bio.UnsafeRun2.ZIORunner
import izumi.logstage.api.IzLogger
import net.playq.tk.concurrent.LoggingZioRunner
import net.playq.tk.concurrent.threadpools.{NamingThreadFactory, MeteredExecutor, ThreadPools}
import zio.{Executor, IO}

import java.util.concurrent.{ThreadFactory, ThreadPoolExecutor}
import scala.concurrent.ExecutionContext

private[plugins] trait ZIORuntimeModule extends ModuleDef {

  make[ZIORunner[Any]].from {
    (cpuPool: MeteredExecutor @Id("cpu"), blockingPool: MeteredExecutor @Id("io"), log: IzLogger) =>
      LoggingZioRunner(cpuPool, blockingPool.executor, log, "main")
  }
  make[UnsafeRun2[IO]].using[ZIORunner[Any]]

  make[UnsafeRun2[IO]].named("blockingIO").from {
    (blockingPool: MeteredExecutor @Id("io"), log: IzLogger) =>
      LoggingZioRunner(blockingPool, blockingPool.executor, log, "blockingIO")
  }

  // Thread Factory

  // We can't name ZScheduler threads since ZIO 2.0
  //  make[ThreadFactory].named("zio").from {
  //    new NamingThreadFactory("zio")
  //  }
  make[ThreadFactory].named("blockingIO").from {
    new NamingThreadFactory("blockingIO")
  }

  // CPU tasks pool
  make[MeteredExecutor].named("cpu").from {
    //    MeteredExecutor(Executor.makeDefault()) // Even though ZIO executor seems to be optimized for globality, we'd rather still make isolated pools, for safety.
    // It doesn't seem to have a closing action for some reason, so not using fromResource here.
    MeteredExecutor(zio.Runtime.defaultExecutor) // Using a global pool after all - at least for now
    // Because having multiple threads with the sames names ZWorker-0..N
    // looks confusing when checking thread saturation in VisualVM
  }
  //  make[MeteredExecutor].named("cpu").fromResource {
  //    (logger: IzLogger) =>
  //      ResourceRewriter
  //        .fromExecutorService(logger, ThreadPools.zio(new NamingThreadFactory("zio")))
  //        .map(ec => MeteredExecutor(Executor.fromThreadPoolExecutor(ec)))
  //  }

  make[Executor].named("cpu").from((_: MeteredExecutor @Id("cpu")).executor)

  // Blocking IO pool
  make[ThreadPoolExecutor].named("blockingIO").fromResource {
    (f: ThreadFactory @Id("blockingIO"), logger: IzLogger) =>
      //      Runtime.defaultBlockingExecutor
      // Even though, zio.Runtime.defaultBlockingExecutor is hardcoded in ZScheduler as the destination for accidentally blocking threads,
      // still try to use a custom pool for explicitly blocking threads.
      ResourceRewriter.fromExecutorService(logger, ThreadPools.blocking(f))
  }

  make[MeteredExecutor].named("io").from {
    (threadPool: ThreadPoolExecutor @Id("blockingIO")) =>
      MeteredExecutor(threadPool)
  }

  make[Executor].named("io").from((_: MeteredExecutor @Id("io")).executor)

  // Execution contexts
  make[ExecutionContext].named("cpu").from((_: MeteredExecutor @Id("cpu")).executor.asExecutionContext)
  make[ExecutionContext].named("blockingIO").from {
    (es: ThreadPoolExecutor @Id("blockingIO")) =>
      ExecutionContext.fromExecutorService(es): ExecutionContext // prevent `ResourceRewriter` rewrite to ExecutorService Resource
  }
  make[ExecutionContext].named("io").using[ExecutionContext]("blockingIO")
  make[ExecutionContext].named("global").from(scala.concurrent.ExecutionContext.global)
}
