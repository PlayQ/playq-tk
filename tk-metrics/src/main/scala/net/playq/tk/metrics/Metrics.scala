package net.playq.tk.metrics

import izumi.functional.bio.{Applicative2, Clock2, F, Monad2}
import net.playq.tk.metrics.Metrics.{AllowMetricAttributesSet, MetricAttributes}

import java.time.temporal.ChronoUnit.MILLIS
import java.time.temporal.Temporal
import java.util.UUID
import java.util.concurrent.TimeUnit.MILLISECONDS
import scala.annotation.{implicitNotFound, unused}
import scala.concurrent.duration.FiniteDuration

trait Metrics[F[_, _]] {
  def inc(label: String, value: Int = 1, attr: MetricAttributes = MetricAttributes.unset)(implicit c: MacroMetricCounter[label.type]): F[Nothing, Unit]
  /** Increments metric and then executes action. */
  def incAnd[E, T](label: String, value: Int = 1, attr: MetricAttributes = MetricAttributes.unset)(f: => F[E, T])(implicit c: MacroMetricCounter[label.type]): F[E, T]
  /** Executes action, and then increments metrics with a guarantee case.
    * Any updates of [[net.playq.tk.metrics.Metrics.MetricAttributes]], stored in fiber local, performed in the action `f`
    * would be applied to the metrics label.
    *
    * @param f Action to be executed before metrics is built and reported. Any metrics context updates performed in this action
    *          would be applied to this metric label
    */
  def incAfter[E, T](label: String, value: Int = 1, attr: MetricAttributes = MetricAttributes.unset)(f: => F[E, T])(implicit c: MacroMetricCounter[label.type]): F[E, T]

  def dec(label: String, value: Int = 1, attr: MetricAttributes = MetricAttributes.unset)(implicit c: MacroMetricCounter[label.type]): F[Nothing, Unit]
  /** Decrements metric and then executes action. */
  def decAnd[E, T](label: String, value: Int = 1, attr: MetricAttributes = MetricAttributes.unset)(f: => F[E, T])(implicit c: MacroMetricCounter[label.type]): F[E, T]
  /** Executes action, and then decrements metrics with a guarantee case.
    * Any updates of [[net.playq.tk.metrics.Metrics.MetricAttributes]], stored in fiber local, performed in the action `f`
    * would be applied to the metrics label.
    *
    * @param f Action to be executed before metrics is built and reported. Any metrics context updates performed in this action
    *          would be applied to this metric label
    */
  def decAfter[E, T](label: String, value: Int = 1, attr: MetricAttributes = MetricAttributes.unset)(f: => F[E, T])(implicit c: MacroMetricCounter[label.type]): F[E, T]

  def mark(label: String, value: Long = 1L, attr: MetricAttributes = MetricAttributes.unset)(implicit m: MacroMetricMeter[label.type]): F[Nothing, Unit]
  /** Marks metric and then executes action. */
  def markAnd[E, T](label: String, value: Long = 1L, attr: MetricAttributes = MetricAttributes.unset)(f: => F[E, T])(implicit m: MacroMetricMeter[label.type]): F[E, T]
  /** Executes action, and then marks metrics with a guarantee case.
    * Any updates of [[net.playq.tk.metrics.Metrics.MetricAttributes]], stored in fiber local, performed in the action `f`
    * would be applied to the metrics label.
    *
    * @param f Action to be executed before metrics is built and reported. Any metrics context updates performed in this action
    *          would be applied to this metric label
    */
  def markAfter[E, T](label: String, value: Long = 1L, attr: MetricAttributes = MetricAttributes.unset)(f: => F[E, T])(implicit m: MacroMetricMeter[label.type]): F[E, T]

  def timerUpdate(label: String, value: FiniteDuration, attr: MetricAttributes = MetricAttributes.unset)(implicit t: MacroMetricTimer[label.type]): F[Nothing, Unit]
  def timerUpdateNanos(label: String, nanos: Long, attr: MetricAttributes      = MetricAttributes.unset)(implicit t: MacroMetricTimer[label.type]): F[Nothing, Unit]
  /** Creates a timer metric and then executes action with a timer bracket. */
  def timerAnd[E, T](label: String, attr: MetricAttributes = MetricAttributes.unset)(f: => F[E, T])(implicit t: MacroMetricTimer[label.type]): F[E, T]
  /** Executes action with a timer bracket, and then updates a timer metric.
    * Any updates of [[net.playq.tk.metrics.Metrics.MetricAttributes]], stored in fiber local, performed in the action `f`
    * would be applied to the metrics label.
    *
    * @param f Action to be executed before metrics is built and reported. Any metrics context updates performed in this action
    *          would be applied to this metric label
    */
  def timerAfter[E, T](label: String, attr: MetricAttributes = MetricAttributes.unset)(f: => F[E, T])(implicit t: MacroMetricTimer[label.type]): F[E, T]

  def record(label: String, value: Long, attr: MetricAttributes = MetricAttributes.unset)(implicit h: MacroMetricHistogram[label.type]): F[Nothing, Unit]

  def setGauge(label: String, attr: MetricAttributes = MetricAttributes.unset)(effect: () => Long)(implicit g: MacroMetricGauge[label.type]): F[Nothing, Unit]

  /** Executes an action `f` with scoped MetricAttributes context.
    * When scope is closed previous state is restored with a bracket case.
    */
  def locally[E, A](attributes: MetricAttributes)(f: => F[E, A]): F[E, A]
  /** Executes an action `f` with scoped MetricAttributes context.
    * When scope is closed previous state is restored with a bracket case.
    */
  def locallyWith[E, A](attributes: MetricAttributes => MetricAttributes)(f: => F[E, A]): F[E, A]
  /** Updates current MetricAttributes context.
    * Might affect parents context, it's better to use `locally` or `locallyWith`.
    */
  def set(attributes: MetricAttributes)(implicit @unused ev: AllowMetricAttributesSet): F[Nothing, Unit]
}

trait MetricsSync {
  def inc(label: String, value: Int        = 1, attr: MetricAttributes)(implicit c: MacroMetricCounter[label.type]): Unit
  def withInc[T](label: String, value: Int = 1, attr: MetricAttributes)(f: => T)(implicit c: MacroMetricCounter[label.type]): T

  def dec(label: String, value: Int        = 1, attr: MetricAttributes)(implicit c: MacroMetricCounter[label.type]): Unit
  def withDec[T](label: String, value: Int = 1, attr: MetricAttributes)(f: => T)(implicit c: MacroMetricCounter[label.type]): T

  def mark(label: String, value: Long        = 1L, attr: MetricAttributes)(implicit m: MacroMetricMeter[label.type]): Unit
  def withMark[T](label: String, value: Long = 1L, attr: MetricAttributes)(f: => T)(implicit m: MacroMetricMeter[label.type]): T

  def timerUpdate(label: String, value: FiniteDuration, attr: MetricAttributes)(implicit t: MacroMetricTimer[label.type]): Unit
  def timerUpdateNanos(label: String, nanos: Long, attr: MetricAttributes)(implicit t: MacroMetricTimer[label.type]): Unit
  def withTimer[T](label: String, attr: MetricAttributes)(f: => T)(implicit t: MacroMetricTimer[label.type]): T

  def record(label: String, value: Long, attr: MetricAttributes)(implicit h: MacroMetricHistogram[label.type]): Unit

  def setGauge(label: String, attr: MetricAttributes)(effect: () => Long)(implicit g: MacroMetricGauge[label.type]): Unit
}

object Metrics {
  def apply[F[_, _]: Metrics]: Metrics[F]        = implicitly
  def empty[F[+_, +_]: Applicative2]: Metrics[F] = new Empty[F]

  trait MetricAttributes {
    def attr: Seq[(String, String)]
    def keys: Set[String] = attr.map(_._1).toSet
    def isEmpty: Boolean  = attr.isEmpty
    def isUnset: Boolean  = false

    final def merge(that: MetricAttributes): MetricAttributes = {
      if (this.isEmpty) {
        that
      } else if (that.isEmpty) {
        this
      } else {
        val otherKeys     = that.keys
        val filteredAttrs = this.attr.filterNot { case (k, _) => otherKeys.contains(k) }
        val merged        = filteredAttrs ++ that.attr
        MetricAttributes.dynamic(merged)
      }
    }
  }
  object MetricAttributes {
    def unset: MetricAttributes = new MetricAttributes {
      override def attr: Seq[(String, String)] = Seq.empty
      override def isUnset: Boolean            = true
    }
    def empty: MetricAttributes = new MetricAttributes {
      override def attr: Seq[(String, String)] = Seq.empty
    }
    def dynamic(d: Seq[(String, String)]): MetricAttributes = new MetricAttributes {
      override def attr: Seq[(String, String)] = d.sortBy(_._1)
    }
    def appId(id: UUID): MetricAttributes = new MetricAttributes {
      override def attr: Seq[(String, String)] = Seq("appId" -> id.toString)
    }
  }

  @implicitNotFound("import net.playq.tk.metrics.Metrics.unsafe.allowMetricAttributesSet to enable MetricAttributes context update.")
  sealed abstract class AllowMetricAttributesSet
  object unsafe {
    implicit val allowMetricAttributesSet: AllowMetricAttributesSet = new AllowMetricAttributesSet {}
  }

  final class Empty[F[+_, +_]: Applicative2] extends Metrics[F] {
    override def inc(label: String, value: Int, attr: MetricAttributes)(implicit c: MacroMetricCounter[label.type]): F[Nothing, Unit]                  = F.unit
    override def dec(label: String, value: Int, attr: MetricAttributes)(implicit c: MacroMetricCounter[label.type]): F[Nothing, Unit]                  = F.unit
    override def mark(label: String, value: Long, attr: MetricAttributes)(implicit m: MacroMetricMeter[label.type]): F[Nothing, Unit]                  = F.unit
    override def record(label: String, value: Long, attr: MetricAttributes)(implicit h: MacroMetricHistogram[label.type]): F[Nothing, Unit]            = F.unit
    override def timerUpdate(label: String, value: FiniteDuration, attr: MetricAttributes)(implicit t: MacroMetricTimer[label.type]): F[Nothing, Unit] = F.unit
    override def timerUpdateNanos(label: String, nanos: Long, attr: MetricAttributes)(implicit t: MacroMetricTimer[label.type]): F[Nothing, Unit]      = F.unit
    override def setGauge(label: String, attr: MetricAttributes)(effect: () => Long)(implicit g: MacroMetricGauge[label.type]): F[Nothing, Unit]       = F.unit

    // preserve suspension
    override def incAnd[E, T](label: String, value: Int, attr: MetricAttributes)(f: => F[E, T])(implicit c: MacroMetricCounter[label.type]): F[E, T]   = F.unit *> f
    override def incAfter[E, T](label: String, value: Int, attr: MetricAttributes)(f: => F[E, T])(implicit c: MacroMetricCounter[label.type]): F[E, T] = F.unit *> f
    override def decAnd[E, T](label: String, value: Int, attr: MetricAttributes)(f: => F[E, T])(implicit c: MacroMetricCounter[label.type]): F[E, T]   = F.unit *> f
    override def decAfter[E, T](label: String, value: Int, attr: MetricAttributes)(f: => F[E, T])(implicit c: MacroMetricCounter[label.type]): F[E, T] = F.unit *> f
    override def markAnd[E, T](label: String, value: Long, attr: MetricAttributes)(f: => F[E, T])(implicit m: MacroMetricMeter[label.type]): F[E, T]   = F.unit *> f
    override def markAfter[E, T](label: String, value: Long, attr: MetricAttributes)(f: => F[E, T])(implicit m: MacroMetricMeter[label.type]): F[E, T] = F.unit *> f
    override def timerAnd[E, T](label: String, attr: MetricAttributes)(f: => F[E, T])(implicit t: MacroMetricTimer[label.type]): F[E, T]               = F.unit *> f
    override def timerAfter[E, T](label: String, attr: MetricAttributes)(f: => F[E, T])(implicit t: MacroMetricTimer[label.type]): F[E, T]             = F.unit *> f

    override def locally[E, A](attributes: MetricAttributes)(f: => F[E, A]): F[E, A]                                = F.unit *> f
    override def locallyWith[E, A](attributes: MetricAttributes => MetricAttributes)(f: => F[E, A]): F[E, A]        = F.unit *> f
    override def set(attributes: MetricAttributes)(implicit @unused ev: AllowMetricAttributesSet): F[Nothing, Unit] = F.unit
  }

  final class EmptySync extends MetricsSync {
    override def inc(label: String, value: Int, attr: MetricAttributes)(implicit c: MacroMetricCounter[label.type]): Unit                  = ()
    override def dec(label: String, value: Int, attr: MetricAttributes)(implicit c: MacroMetricCounter[label.type]): Unit                  = ()
    override def mark(label: String, value: Long, attr: MetricAttributes)(implicit m: MacroMetricMeter[label.type]): Unit                  = ()
    override def record(label: String, value: Long, attr: MetricAttributes)(implicit h: MacroMetricHistogram[label.type]): Unit            = ()
    override def timerUpdate(label: String, value: FiniteDuration, attr: MetricAttributes)(implicit t: MacroMetricTimer[label.type]): Unit = ()
    override def timerUpdateNanos(label: String, nanos: Long, attr: MetricAttributes)(implicit t: MacroMetricTimer[label.type]): Unit      = ()
    override def setGauge(label: String, attr: MetricAttributes)(effect: () => Long)(implicit g: MacroMetricGauge[label.type]): Unit       = ()

    override def withTimer[T](label: String, attr: MetricAttributes)(f: => T)(implicit t: MacroMetricTimer[label.type]): T             = f
    override def withMark[T](label: String, value: Long, attr: MetricAttributes)(f: => T)(implicit m: MacroMetricMeter[label.type]): T = f
    override def withInc[T](label: String, value: Int, attr: MetricAttributes)(f: => T)(implicit c: MacroMetricCounter[label.type]): T = f
    override def withDec[T](label: String, value: Int, attr: MetricAttributes)(f: => T)(implicit c: MacroMetricCounter[label.type]): T = f
  }

  implicit final class TimerUpdateDiff[F[+_, +_]](private val metrics: Metrics[F]) extends AnyVal {
    def timerUpdateDiff(
      metric: String,
      start: Temporal,
      end: Temporal,
      attr: MetricAttributes = MetricAttributes.unset,
    )(implicit macroSaveTimerMetric: MacroMetricTimer[metric.type]
    ): F[Nothing, Unit] = {
      val duration = FiniteDuration(MILLIS.between(start, end), MILLISECONDS)
      metrics.timerUpdate(metric, duration, attr)
    }

    def timerUpdateDiffNow(
      metric: String,
      start: Temporal,
      attr: MetricAttributes = MetricAttributes.unset,
    )(implicit
      macroSaveTimerMetric: MacroMetricTimer[metric.type],
      clock: Clock2[F],
      F: Monad2[F],
    ): F[Nothing, Unit] = {
      for {
        end     <- clock.nowZoned()
        duration = FiniteDuration(MILLIS.between(start, end), MILLISECONDS)
        _       <- metrics.timerUpdate(metric, duration, attr)
      } yield ()
    }
  }
}
