package net.playq.tk.concurrent

import izumi.functional.bio.UnsafeRun2.{FailureHandler, ZIORunner}
import izumi.functional.bio.{Exit, UnsafeRun2}
import logstage.IzLogger
import net.playq.tk.concurrent.threadpools.MeteredExecutor
import zio.{Executor, Unsafe, ZEnvironment}

import scala.util.control.NonFatal

object LoggingZioRunner {
  def apply(
    cpuPool: MeteredExecutor,
    blockingPool: Executor,
    logger: IzLogger,
    clue: String,
  ): ZIORunner[Any] = {
    val actualAvailableCores = Runtime.getRuntime.availableProcessors()
    val cpuPoolCoresCount    = cpuPool.executor.metrics(using Unsafe.unsafe(identity)).get.concurrency

    val customHandler = FailureHandler.Custom {
      // log non-fatal errors with debug level, as ZIO keep raising errors from supervised fibers
      case Exit.Error(NonFatal(error), trace) =>
        logger.debug(s"$clue: fiber errored out due to unhandled $error $trace")
      case Exit.Error(error: Throwable, trace) =>
        logger.warn(s"$clue: fiber errored out due to unhandled $error $trace")

      // log domain errors with debug level, as ZIO keep raising errors from supervised fibers
      case Exit.Error(error, trace) =>
        logger.debug(s"$clue: fiber errored out due to unhandled domain $error $trace")

      case Exit.Termination(defect, _, trace) =>
        logger.warn(s"$clue: fiber terminated erroneously with unhandled $defect $trace")
      case Exit.Interruption(defect, _, trace) =>
        logger.trace(s"$clue: fiber interrupted erroneously with unhandled $defect $trace")
    }

    logger.info(s"Creating LoggingZioRunner ($clue) with $cpuPoolCoresCount $actualAvailableCores")

    UnsafeRun2.createZIO(
      customCpuPool             = Some(cpuPool.executor),
      customBlockingPool        = Some(blockingPool),
      handler                   = customHandler,
      otherRuntimeConfiguration = Nil,
      initialEnv                = ZEnvironment.empty,
    )
  }
}
