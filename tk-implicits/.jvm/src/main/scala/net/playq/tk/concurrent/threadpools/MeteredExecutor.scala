package net.playq.tk.concurrent.threadpools

import zio.internal.ExecutionMetrics
import zio.{Executor, Unsafe}

import java.util.concurrent.ThreadPoolExecutor

final class MeteredExecutor(val executor: Executor { def metrics(implicit unsafe: Unsafe): Some[ExecutionMetrics] }) extends AnyVal

object MeteredExecutor {
  def apply(threadPoolExecutor: ThreadPoolExecutor): MeteredExecutor = {
    MeteredExecutor(Executor.fromThreadPoolExecutor(threadPoolExecutor))
  }

  def apply(executor: Executor): MeteredExecutor = {
    if (executor.metrics(using Unsafe.unsafe(identity)).isDefined) {
      new MeteredExecutor(executor.asInstanceOf[Executor { def metrics(implicit unsafe: Unsafe): Some[ExecutionMetrics] }])
    } else {
      throw new RuntimeException(s"""Executor=$executor does not expose metrics!
                                    |Executor should be created via zio.PlatformLive.ExecutorUtil.fromThreadPoolExecutor!
                                    |""".stripMargin)
    }
  }
}
