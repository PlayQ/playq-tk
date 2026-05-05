package net.playq.tk.metrics

import net.playq.tk.metrics.base.MetricDef
import net.playq.tk.metrics.base.MetricDef.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

final class MetricsCodecTest extends AnyWordSpec with ScalaCheckPropertyChecks {
  implicit val arbitraryTupple: Gen[(String, String, Double)] = {
    for {
      role  <- Gen.alphaStr.filter(_.nonEmpty)
      label <- Gen.alphaStr.filter(_.nonEmpty)
      init  <- Gen.posNum[Int]
    } yield (role, label, init.toDouble)
  }
  implicit val MetricCounterArb: Arbitrary[MetricCounter]     = Arbitrary(arbitraryTupple.map { case (r, l, i) => MetricCounter(r, l, i.toInt) })
  implicit val MetricHistogramArb: Arbitrary[MetricHistogram] = Arbitrary(arbitraryTupple.map { case (r, l, i) => MetricHistogram(r, l, i) })
  implicit val MetricTimerArb: Arbitrary[MetricTimer]         = Arbitrary(arbitraryTupple.map { case (r, l, i) => MetricTimer(r, l, i) })
  implicit val MetricMeterArb: Arbitrary[MetricMeter]         = Arbitrary(arbitraryTupple.map { case (r, l, i) => MetricMeter(r, l, i) })
  implicit val MetricGaugeArb: Arbitrary[MetricGauge]         = Arbitrary(arbitraryTupple.map { case (r, l, i) => MetricGauge(r, l, i) })
  implicit val MetricDefArb: Arbitrary[MetricDef]             = Arbitrary {
    Gen.oneOf(MetricCounterArb.arbitrary, MetricHistogramArb.arbitrary, MetricTimerArb.arbitrary, MetricMeterArb.arbitrary, MetricGaugeArb.arbitrary)
  }

  "Decode and encode metrics properly" in {
    forAll {
      (metric: MetricDef) =>
        val encoded = MetricDef.encode(metric)
        val decoded = MetricDef.decode(encoded)
        assert(decoded.contains(metric))
    }
  }
}
