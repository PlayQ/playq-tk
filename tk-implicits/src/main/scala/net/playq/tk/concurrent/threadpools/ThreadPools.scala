package net.playq.tk.concurrent.threadpools

import java.util.concurrent._

object ThreadPools {
  /** "virtually" unbounded thread pool to put blocking IO tasks on.
    * Does not use `Int.MaxValue` to trade possible rejections in favor of avoiding OS pool exhaustion *
    */
  def blocking(threadFactory: ThreadFactory, keepAliveSeconds: Int = 60): ThreadPoolExecutor = {
    new ThreadPoolExecutor(
      0,
      2000,
      keepAliveSeconds.toLong,
      TimeUnit.SECONDS,
      new SynchronousQueue[Runnable](false),
      threadFactory,
    )
  }

  def zio(
    threadFactory: ThreadFactory,
    queueSize: Int        = 10000,
    keepAliveSeconds: Int = 60,
    cores: Int            = Runtime.getRuntime.availableProcessors.max(2),
  ): ThreadPoolExecutor = {

    val executor = new ThreadPoolExecutor(
      cores,
      cores // no point setting max pool size, because the pool will never grow
      // (ThreadPoolExecutor starts growing after the queue is full, but the queue size is 1k)
      ,
      keepAliveSeconds.toLong,
      TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable](queueSize),
      threadFactory,
    )
    executor
  }

  def zioTimer(threadFactory: ThreadFactory, coreSize: Int = 1): ScheduledExecutorService = {
    Executors.newScheduledThreadPool(coreSize, threadFactory)
  }

  /** same as zio but with short-living threads via 1 second keep-alive & enabled core timeout */
  def ziotest(
    threadFactory: ThreadFactory,
    queueSize: Int        = 10000,
    keepAliveSeconds: Int = 1,
    cores: Int            = Runtime.getRuntime.availableProcessors.max(2),
  ): ThreadPoolExecutor = {
    val executor = zio(threadFactory, queueSize, keepAliveSeconds, cores)
    executor.allowCoreThreadTimeOut(true)
    executor
  }

}
