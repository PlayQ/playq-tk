package net.playq.tk.concurrent.threadpools

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

final class NamingThreadFactory(name: String) extends ThreadFactory {
  private val s: SecurityManager = System.getSecurityManager

  private val parentGroup: ThreadGroup = {
    if (s != null) {
      s.getThreadGroup
    } else {
      Thread.currentThread.getThreadGroup
    }
  }

  final private val group = new ThreadGroup(parentGroup, name)

  final private val threadNumber = new AtomicInteger(1)

  def spawnedThreads: Int = {
    threadNumber.get() - 1
  }

  override def newThread(r: Runnable): Thread = {
    // hashcode added to make names unique in case of duplicated name
    val t = new Thread(group, r, s"$name-${Integer.toUnsignedString(hashCode())}-${threadNumber.getAndIncrement}", 0)
    t.setDaemon(true)
    if (t.getPriority != Thread.NORM_PRIORITY) {
      t.setPriority(Thread.NORM_PRIORITY)
    }
    t
  }
}
