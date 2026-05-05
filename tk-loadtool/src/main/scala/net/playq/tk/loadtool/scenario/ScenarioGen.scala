package net.playq.tk.loadtool.scenario

import distage.{ClassConstructor, Tag}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{DIKey, TypedRef}

import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}
import scala.annotation.unused

abstract sealed class ScenarioGen[+T](implicit tag: Tag[T]) {
  val key: DIKey = DIKey[T]
}

object ScenarioGen {

  final class FromFunctoid[T](val fun: Functoid[T])(implicit val tag: Tag[T]) extends ScenarioGen[T]

  abstract sealed class Gen[+T](implicit tag: Tag[T]) extends ScenarioGen {
    protected def next: T
    final def nextValueTyped: TypedRef[T] = TypedRef(nextValue, key.tpe, isByName = false)
    def nextValue: T                      = next
    def recharge(@unused size: Int): Unit = ()
  }

  abstract class GenBuffered[+T](implicit tag: Tag[T]) extends Gen[T] {
    private[this] val queue         = scala.collection.mutable.Queue.empty[T]
    final override def nextValue: T = {
      queue.synchronized {
        if (queue.size < 1) next else queue.dequeue()
      }
    }
    final override def recharge(size: Int): Unit = {
      queue.synchronized {
        queue.enqueueAll((1 to (size - queue.size)).map(_ => next))
        ()
      }
    }
  }

  def apply[T: Tag](fun: => T): ScenarioGen.Gen[T] = new GenBuffered[T] {
    def next: T                   = fun
    override def toString: String = fun.toString
  }
  def const[T: Tag](v: T): ScenarioGen.Gen[T]                     = new Const[T](v)
  def from[T: Tag](fun: Functoid[T]): ScenarioGen.FromFunctoid[T] = new FromFunctoid[T](fun)
  def of[T: ClassConstructor: Tag]: ScenarioGen.FromFunctoid[T]   = new FromFunctoid[T](ClassConstructor[T])

  final class Const[T](val next: T)(implicit val tag: Tag[T]) extends Gen[T]

  final class IntGen extends ScenarioGen.GenBuffered[Int] {
    private[this] val ref = new AtomicInteger(scala.util.Random.nextInt())
    def next: Int         = ref.getAndUpdate(i => if (i == Integer.MAX_VALUE) Integer.MIN_VALUE else i + 1)
  }
  final class LongGen extends ScenarioGen.GenBuffered[Long] {
    private[this] val ref = new AtomicLong(scala.util.Random.nextLong())
    def next: Long        = ref.getAndUpdate(i => if (i == Long.MaxValue) Long.MinValue else i + 1L)
  }
  final class UUIDGen extends ScenarioGen.GenBuffered[UUID] {
    private[this] val ref1 = new LongGen
    private[this] val ref2 = new LongGen
    def next: UUID         = new UUID(ref1.next, ref2.next)
  }
  final class BooleanGen extends ScenarioGen.GenBuffered[Boolean] {
    private[this] val ref      = new AtomicBoolean(scala.util.Random.nextBoolean())
    override def next: Boolean = ref.getAndSet(scala.util.Random.nextBoolean())
  }
  // TODO: make it alphanumerical?
  final class StringGen extends ScenarioGen.GenBuffered[String] {
    private[this] val ref     = new IntGen
    override def next: String = ref.next.toString
  }
  final class DoubleGen extends ScenarioGen.GenBuffered[Double] {
    private[this] val ref     = new LongGen
    override def next: Double = ref.next.toDouble / 0.99d
  }
  final class ShortGen extends ScenarioGen.GenBuffered[Short] {
    private[this] val ref    = new AtomicInteger(scala.util.Random.nextInt())
    override def next: Short = ref.updateAndGet(i => if (i.toShort == Short.MaxValue) Short.MinValue else i.toShort + 1).toShort
  }
  final class ByteGen extends ScenarioGen.GenBuffered[Byte] {
    private[this] val ref   = new AtomicInteger(scala.util.Random.nextInt())
    override def next: Byte = ref.updateAndGet(i => if (i.toByte == Byte.MaxValue) Byte.MinValue else i.toByte + 1).toByte
  }
}
