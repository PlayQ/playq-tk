package net.playq.tk.metrics

import net.playq.tk.metrics.Metrics.MetricAttributes
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration.DurationInt

class BasicMetricMacroTest extends AnyWordSpec {

  "basic metrics macro" should {

    "record a timer metric" in {
      val dummyMetrics = new Metrics.EmptySync()
      dummyMetrics.timerUpdate("abc", 1.second, MetricAttributes.empty)
    }

    "correct subclass is generated in timer metric macro" in {
      val superClass  = classOf[MacroMetricTimer["abc"]]
      val timerMetric = implicitly[MacroMetricTimer["abc"]]
      assert(superClass.isAssignableFrom(timerMetric.getClass))
    }

  }

}
