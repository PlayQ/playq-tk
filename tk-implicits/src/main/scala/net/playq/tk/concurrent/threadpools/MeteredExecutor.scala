package net.playq.tk.concurrent.threadpools

import java.util.concurrent.ThreadPoolExecutor

import zio.internal.{ExecutionMetrics, Executor}

final class MeteredExecutor(val executor: Executor { def metrics: Some[ExecutionMetrics] }) extends AnyVal

object MeteredExecutor {
  def apply(threadPoolExecutor: ThreadPoolExecutor, yieldOpCount: Int): MeteredExecutor = {
    MeteredExecutor(Executor.fromThreadPoolExecutor(_ => yieldOpCount)(threadPoolExecutor))
  }

  def apply(executor: Executor): MeteredExecutor = {
    if (executor.metrics.isDefined) {
      new MeteredExecutor(executor.asInstanceOf[Executor { def metrics: Some[ExecutionMetrics] }])
    } else {
      throw new RuntimeException(s"""Executor=$executor does not expose metrics!
                                    |Executor should be created via zio.PlatformLive.ExecutorUtil.fromThreadPoolExecutor!
                                    |""".stripMargin)
    }
  }
}
