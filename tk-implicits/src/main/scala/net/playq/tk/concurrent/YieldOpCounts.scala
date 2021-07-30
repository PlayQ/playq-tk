package net.playq.tk.concurrent

trait YieldOpCounts {
  def zioYieldOpCount: Int      = 1024
  def blockingYieldOpCount: Int = Int.MaxValue
}

object YieldOpCounts extends YieldOpCounts
