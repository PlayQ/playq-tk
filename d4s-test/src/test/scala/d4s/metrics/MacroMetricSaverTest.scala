package d4s.metrics

import izumi.fundamentals.platform.language.Quirks.*
import net.playq.tk.metrics.macrodefs.MacroMetricBase
import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn

@nowarn("msg=parameter x.4 in anonymous function is never used") // Quirk with Scala 2 version of `assertCompiles`
final class MacroMetricSaverTest extends AnyWordSpec {
  private[this] val str: String = s"${1} x ${2}"
  str.discard()

  private[this] final val constant = "1 x 2"
  constant.discard()

  "print an error message mentioning discarder when used with a non-constant type" in {
    val t = intercept[TestFailedException](assertCompiles("implicitly[net.playq.tk.metrics.MacroMetricCounter[str.type]]"))
    assert(t.getMessage.contains("discarded"))
  }

  "succeed when using a constant type val" in {
    assertCompiles("implicitly[net.playq.tk.metrics.MacroMetricCounter[constant.type]]")
  }

  "print an error message if there is no implicit for mentioned metric" in {
    val t1 = intercept[TestFailedException](assertCompiles("implicitly[MacroMetricSaverTest.TestMetric.MetricBase[str.type, Nothing]]"))
    assert(t1.getMessage.contains("import Nothing.discarded.* to disable"))
    val t2 = intercept[TestFailedException](assertCompiles("implicitly[net.playq.tk.metrics.MacroMetricDynamoMeter[str.type]]"))
    assert(t2.getMessage.contains("import net.playq.tk.metrics.domain.MacroMetricsDynamo.discarded.*"))
  }

}

object MacroMetricSaverTest {
  object TestMetric extends MacroMetricBase.Counter {
    override object CompileTime extends CompileTime
  }
}
