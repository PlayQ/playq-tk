package net.playq.tk.concurrent

import java.util.concurrent.ThreadPoolExecutor

import izumi.functional.bio.Exit
import izumi.functional.bio.UnsafeRun2.{FailureHandler, ZIOPlatform, ZIORunner}
import logstage.IzLogger
import zio.internal.tracing.TracingConfig

object LoggingZioRunner {
  def apply(
    cpuPool: ThreadPoolExecutor,
    logger: IzLogger,
    tracingConfig: TracingConfig,
    yieldOpCounts: YieldOpCounts,
  ): ZIORunner = {
    val actualAvailableCores = Runtime.getRuntime.availableProcessors()
    val cpuPoolCoresCount    = cpuPool.getCorePoolSize

    val customHandler = FailureHandler.Custom {
      case Exit.Error(error, trace) =>
        logger.warn(s"Fiber errored out due to unhandled $error $trace")
      case Exit.Termination(defect, _, trace) =>
        logger.warn(s"Fiber terminated erroneously with unhandled $defect $trace")
      case Exit.Interruption(defect, trace) =>
        logger.trace(s"Fiber interrupted erroneously with unhandled $defect $trace")
    }

    logger.info(s"Creating LoggingZioRunner with $cpuPoolCoresCount $actualAvailableCores")

    new ZIORunner(new ZIOPlatform(cpuPool, customHandler, yieldOpCounts.zioYieldOpCount, tracingConfig))
  }
}
