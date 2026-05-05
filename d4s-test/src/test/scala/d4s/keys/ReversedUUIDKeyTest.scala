package d4s.keys

import izumi.functional.bio.Entropy1
import org.scalacheck.Prop.AnyOperators
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID

final class ReversedUUIDKeyTest extends AnyWordSpec with Checkers {

  // test 10k times
  implicit val propertyConf: PropertyCheckConfiguration = {
    generatorDrivenConfig.copy(minSuccessful = 10000)
  }

  "ReversedUUIDKey has the opposite lexicographic ordering as the UUID's usual ordering" in check {
    (uuids: List[UUID]) =>
      val sortedUUIDs = uuids.sorted

      val strings       = sortedUUIDs.map(ReversedUUIDKey(_).asString)
      val sortedStrings = strings.sorted

      sortedStrings ?= strings.reverse
  }

  "ReversedUUIDKey preserves values" in check {
    (uuids: List[UUID]) =>
      val orderedStrs = uuids.map(ReversedUUIDKey(_))
      val parsedUuids = orderedStrs.map(ReversedUUIDKey.parse)

      uuids ?= parsedUuids
  }

  "TimeUUID ordering" in check {
    (_: Unit) =>
      val uuids = List.fill(20)(Entropy1.Standard.nextTimeUUID()).sorted
      val strs  = uuids.map(ReversedUUIDKey(_).asString)

      val sortedUUID = uuids.map(ReversedUUIDKey(_).asString)
      val sortedSTR  = strs.sorted

      sortedUUID ?= sortedSTR.reverse
  }

}
