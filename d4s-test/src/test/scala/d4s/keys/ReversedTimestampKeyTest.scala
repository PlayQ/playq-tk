package d4s.keys

import izumi.fundamentals.platform.time.IzTime.zonedDateTimeOrdering
import net.playq.tk.test.rnd.TkRndBase
import org.scalacheck.Prop.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.Checkers

import java.time.ZonedDateTime

final class ReversedTimestampKeyTest extends AnyWordSpec with Checkers with TkRndBase {

  // test 10k times
  implicit val propertyConf: PropertyCheckConfiguration = {
    generatorDrivenConfig.copy(minSuccessful = 10000)
  }

  "negated timestamp has opposite lexicographic ordering to usual timestamp" in check {
    times: List[ZonedDateTime] =>
      val sortedTs = times.sorted

      val negates       = sortedTs.map(ReversedTimestampKey(_).asString)
      val sortedNegates = negates.sorted

      sortedNegates ?= negates.reverse
  }

}
