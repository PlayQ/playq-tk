package net.playq.tk.test.rnd

import izumi.functional.bio.{Clock1, Entropy2, Functor2, IO2}
import net.playq.tk.test.rnd.TkRndAuto.DerivedArbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.rng.Seed
import org.scalacheck.{Arbitrary, Gen}

import java.net.InetAddress
import java.time.{LocalDate, OffsetDateTime, ZonedDateTime}
import java.util.UUID

trait TkRndBase {
  self =>

  def derivedArb[T: DerivedArbitrary]: Arbitrary[T] = DerivedArbitrary[T].arbitrary

  def random[Z: Arbitrary]: Z = arbitrary[Z].pureApply(tkGenParams, Seed.random())

  def random[ZN: ArbitraryOfN](size: Int): ZN = random[ZN](using ArbitraryOfN.arbitraryOfN[ZN](size))

  implicit final class FRandom[F[+_, +_]](private val F: IO2[F]) {
    def random[Z: Arbitrary]: F[Nothing, Z] = F.sync(self.random[Z])

    def random[ZN: ArbitraryOfN](size: Int): F[Nothing, ZN] = random[ZN](using ArbitraryOfN.arbitraryOfN[ZN](size))
  }

  def randomWithSeed[Z: Arbitrary](l: Long): Z = {
    arbitrary[Z].pureApply(tkGenParams, Seed(l))
  }

  def randomEntropy2[F[+_, +_]: Entropy2: Functor2, Z](instance: Arbitrary[Z]): F[Nothing, Z] = {
    Entropy2[F].nextLong().map(randomWithSeed(_)(using instance))
  }
  def randomEntropy2[F[+_, +_]: Entropy2: Functor2, Z: Arbitrary]: F[Nothing, Z] = {
    randomEntropy2(implicitly[Arbitrary[Z]])
  }

  val tkGenParams: Gen.Parameters = Gen.Parameters.default.disableLegacyShrinking

  implicit val charArb: Arbitrary[Char] = Arbitrary(Gen.alphaNumChar)

  implicit val stringArb: Arbitrary[String] = Arbitrary(Gen.alphaNumStr.filter(_.nonEmpty))

  implicit val arbitraryUUID: Arbitrary[UUID] = Arbitrary(Gen.uuid)

  implicit val arbitraryIzTime: Arbitrary[ZonedDateTime] = Arbitrary {
    for {
      year  <- Gen.choose(1900, 2100)
      month <- Gen.choose(1, 12)
      day   <- Gen.choose(1, 28)
      hour  <- Gen.choose(0, 23)
      min   <- Gen.choose(0, 59)
      sec   <- Gen.choose(0, 59)
    } yield {
      ZonedDateTime.of(year, month, day, hour, min, sec, 0, Clock1.TZ_UTC)
    }
  }

  implicit val arbitraryOffsetDateTime: Arbitrary[OffsetDateTime] = Arbitrary(arbitraryIzTime.arbitrary.map(_.toOffsetDateTime))

  implicit val arbitraryLocalDate: Arbitrary[LocalDate] = Arbitrary {
    for {
      year  <- Gen.choose(1900, 2100)
      month <- Gen.choose(1, 12)
      day   <- Gen.choose(1, 28)
    } yield {
      LocalDate.of(year, month, day)
    }
  }

  implicit val arbitraryInetAddress: Arbitrary[InetAddress] = Arbitrary {
    val ipGen = Gen.posNum[Byte].filter(_ > 0)
    Gen.containerOfN[Array, Byte](4, ipGen).map(InetAddress.getByAddress)
  }
}
