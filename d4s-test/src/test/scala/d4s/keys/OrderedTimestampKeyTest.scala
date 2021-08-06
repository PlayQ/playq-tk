package d4s.keys

import izumi.fundamentals.platform.time.IzTime.zonedDateTimeOrdering
import net.playq.tk.test.rnd.TkRndBase
import org.scalacheck.Prop._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.Checkers

import java.time.ZonedDateTime

final class OrderedTimestampKeyTest extends AnyWordSpec with Checkers with TkRndBase {
  // test 10k times
  implicit val propertyConf: PropertyCheckConfiguration = {
    generatorDrivenConfig.copy(minSuccessful = 10000)
  }

  "digit timestamp has has the lexicographic ordering as usual timestamp ordering" in check {
    times: List[ZonedDateTime] =>
      val sortedTs = times.sorted

      val negates       = sortedTs.map(ReversedTimestampKey(_).asString)
      val sortedNegates = negates.sorted

      sortedNegates ?= negates.reverse
  }

}
