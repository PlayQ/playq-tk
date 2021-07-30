package net.playq.tk.loadtool.scenario.gen

import java.util.UUID
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import distage.Tag
import izumi.distage.model.reflection.DIKey

abstract class ScenarioGen[T: Tag] {
  final val key: DIKey = DIKey[T]
  def next: T
}

object ScenarioGen {
  def apply[T: Tag](fun: => T): ScenarioGen[T] = new ScenarioGen[T] {
    def next: T                   = fun
    override def toString: String = fun.toString
  }

  final class IntGen extends ScenarioGen[Int] {
    private[this] val ref = new AtomicInteger(scala.util.Random.nextInt())
    def next: Int         = ref.getAndUpdate(i => if (i == Integer.MAX_VALUE) Integer.MIN_VALUE else i + 1)
  }
  final class LongGen extends ScenarioGen[Long] {
    private[this] val ref = new AtomicLong(scala.util.Random.nextLong())
    def next: Long        = ref.getAndUpdate(i => if (i == Long.MaxValue) Long.MinValue else i + 1L)
  }
  final class UUIDGen extends ScenarioGen[UUID] {
    private[this] val ref1 = new LongGen
    private[this] val ref2 = new LongGen
    def next: UUID         = new UUID(ref1.next, ref2.next)
  }
}
