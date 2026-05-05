package d4s.codecs

import io.circe.generic.semiauto
import io.circe.Codec
import net.playq.tk.test.rnd.TkRndBase
import org.scalacheck.Arbitrary

object Fixtures extends TkRndBase {

  final case class AmbiguousResult(v: Either[String, Int])
  object AmbiguousResult {
    implicit lazy val arbitrary: Arbitrary[AmbiguousResult] = derivedArb[AmbiguousResult]
  }

  final case class TestCaseClass(
    a: Int,
    b: Long,
    c: Boolean,
    d: String,
    e: Seq[Int],
    f: Option[TestDataWrap],
    m: Map[String, Int],
    wrap: TestDataWrap,
  )
  object TestCaseClass {
    implicit val circeCodec: Codec.AsObject[TestCaseClass] = semiauto.deriveCodec[TestCaseClass]
    implicit lazy val arbitrary: Arbitrary[TestCaseClass]  = derivedArb[TestCaseClass]
  }

  final case class TestDataWrap(data: String)
  object TestDataWrap {
    implicit val circeCodec: Codec.AsObject[TestDataWrap]       = semiauto.deriveCodec[TestDataWrap]
    implicit val magnoliaCodec: D4SCodec.AsObject[TestDataWrap] = D4SCodec.AsObject.derived[TestDataWrap]
    implicit lazy val arbitrary: Arbitrary[TestDataWrap]        = derivedArb[TestDataWrap]
  }

  final case class TestByteArray(a: Array[Byte])
  object TestByteArray {
    implicit lazy val arbitrary: Arbitrary[TestByteArray] = derivedArb[TestByteArray]
  }

  final case class TestDouble(a: Double)
  object TestDouble {
    implicit val circeCodec: Codec.AsObject[TestDouble] = semiauto.deriveCodec[TestDouble]
    implicit lazy val arbitrary: Arbitrary[TestDouble]  = derivedArb[TestDouble]
  }

  final case class TestBinarySet(a: Set[Array[Byte]])
  object TestBinarySet extends WithD4S[TestBinarySet] {
    implicit lazy val arbitrary: Arbitrary[TestBinarySet] = derivedArb[TestBinarySet]
  }

  final case class TestStringSet(a: Set[String])
  object TestStringSet extends WithD4S[TestStringSet] {
    implicit lazy val arbitrary: Arbitrary[TestStringSet] = derivedArb[TestStringSet]
  }

}
