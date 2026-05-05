package net.playq.tk.metrics.macrodefs

import net.playq.tk.metrics.base.MetricDef
import net.playq.tk.metrics.base.MetricDef.{MetricCounter, MetricGauge, MetricHistogram, MetricMeter, MetricTimer}

import net.playq.tk.metrics.*

import scala.quoted.{Expr, Quotes, Type}
import scala.annotation.implicitNotFound

sealed trait MacroMetricBase {
  self =>

  def createMetrics(role: String, label: String): List[MetricDef]
  def createLabel(label: String): String = label

  @implicitNotFound("import ${DiscardType}.discarded.* to disable metric recording for ${S} - a metric created like this will not be visible in MetricsApi")
  trait MetricBase[S <: String, +DiscardType] {
    def get: Option[String]
  }

  def empty[S <: String]: MetricBase[S, Any] = new MetricBase[S, Nothing] {
    override val get: Option[String] = None
  }

  val CompileTime: CompileTime
  abstract class CompileTime protected[this] {

    def materializeImpl[S <: String: Type, SelfType <: self.type: Type, DiscardType: Type](using quotes: Quotes): Expr[self.MetricBase[S, DiscardType]] = {
      import quotes.reflect.*

      val selfTypeRepr = TypeRepr.of[SelfType]
      val selfExpr     = This(selfTypeRepr.typeSymbol).asExprOf[SelfType]

      val selfTypeName    = selfTypeRepr.dealias.simplified.show
      val discardTypeName = TypeRepr.of[DiscardType].dealias.simplified.show
      val label           = MacroMetricSaver.getConstantType[S](selfTypeName, discardTypeName)
      val labelExpr       = Expr(createLabel(label))

      MacroMetricSaver.getRoles().foreach {
        role =>
          MacroMetricSaver.writeToFile(createMetrics(role, label))
      }

      '{
//        val prefix: SelfType = ${ selfExpr }
        // workaround for https://github.com/lampepfl/dotty/issues/17293
        val prefix: MacroMetricBase = $selfExpr
        new prefix.MetricBase[S, DiscardType] {
          def get: Option[String] = Some($labelExpr)
        }
      }.asInstanceOf[Expr[self.MetricBase[S, DiscardType]]]
    }

  }
}

object MacroMetricBase {

  trait Timer extends MacroMetricBase {
    override def createMetrics(role: String, label: String): List[MetricDef] = List(MetricTimer(role, createLabel(label), 0.0))
  }
  object Timer extends Timer {
    inline implicit def materialize[S <: String]: MetricBase[S, discarded.type] = ${ CompileTime.materializeImpl[S, this.type, discarded.type] }
    override object CompileTime extends CompileTime
  }

  trait Counter extends MacroMetricBase {
    override def createMetrics(role: String, label: String): List[MetricDef] = List(MetricCounter(role, createLabel(label), 0))
  }
  object Counter extends Counter {
    inline implicit def materialize[S <: String]: MetricBase[S, discarded.type] = ${ CompileTime.materializeImpl[S, this.type, discarded.type] }
    override object CompileTime extends CompileTime
  }

  trait Meter extends MacroMetricBase {
    override def createMetrics(role: String, label: String): List[MetricDef] = List(MetricMeter(role, createLabel(label), 0.0))
  }
  object Meter extends Meter {
    inline implicit def materialize[S <: String]: MetricBase[S, discarded.type] = ${ CompileTime.materializeImpl[S, this.type, discarded.type] }
    override object CompileTime extends CompileTime
  }

  trait Gauge extends MacroMetricBase {
    override def createMetrics(role: String, label: String): List[MetricDef] = List(MetricGauge(role, createLabel(label), 0.0))
  }
  object Gauge extends Gauge {
    inline implicit def materialize[S <: String]: MetricBase[S, discarded.type] = ${ CompileTime.materializeImpl[S, this.type, discarded.type] }
    override object CompileTime extends CompileTime
  }

  trait Histogram extends MacroMetricBase {
    override def createMetrics(role: String, label: String): List[MetricDef] = List(MetricHistogram(role, createLabel(label), 0.0))
  }
  object Histogram extends Histogram {
    inline implicit def materialize[S <: String]: MetricBase[S, discarded.type] = ${ CompileTime.materializeImpl[S, this.type, discarded.type] }
    override object CompileTime extends CompileTime
  }

  object discarded {
    implicit def matCounter[S <: String]: MacroMetricCounter[S]     = Counter.empty[S]
    implicit def matTimer[S <: String]: MacroMetricTimer[S]         = Timer.empty[S]
    implicit def matMeter[S <: String]: MacroMetricMeter[S]         = Meter.empty[S]
    implicit def matGauge[S <: String]: MacroMetricGauge[S]         = Gauge.empty[S]
    implicit def matHistogram[S <: String]: MacroMetricHistogram[S] = Histogram.empty[S]
  }

}
