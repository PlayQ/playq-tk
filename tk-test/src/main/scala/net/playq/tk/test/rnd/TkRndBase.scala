package net.playq.tk.test.rnd

import izumi.functional.bio.{Entropy2, Functor2, IO3}
import izumi.fundamentals.platform.time.IzTime
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.derive.MkArbitrary
import org.scalacheck.rng.Seed
import org.scalacheck.{Arbitrary, Gen}

import java.net.InetAddress
import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

trait TkRndBase { self =>

  def arb[T: MkArbitrary]: Arbitrary[T] = MkArbitrary[T].arbitrary

  def random[Z: Arbitrary]: Z = arbitrary[Z].pureApply(Gen.Parameters.default, Seed.random())

  implicit final class FRandom[F[-_, +_, +_]](private val F: IO3[F]) {
    def random[Z: Arbitrary]: F[Any, Nothing, Z] = F.sync(self.random[Z])
  }

  def randomWithSeed[Z: Arbitrary](l: Long): Z = {
    arbitrary[Z].pureApply(Gen.Parameters.default, Seed(l))
  }

  def randomEntropy2[F[+_, +_]: Entropy2: Functor2, Z](instance: Arbitrary[Z]): F[Nothing, Z] = {
    Entropy2[F].nextLong().map(randomWithSeed(_)(instance))
  }
  def randomEntropy2[F[+_, +_]: Entropy2: Functor2, Z: Arbitrary]: F[Nothing, Z] = {
    randomEntropy2(implicitly[Arbitrary[Z]])
  }

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
      ZonedDateTime.of(year, month, day, hour, min, sec, 0, IzTime.TZ_UTC)
    }
  }

  implicit val arbitraryLocalDate: Arbitrary[LocalDate] = Arbitrary {
    for {
      year  <- Gen.choose(1900, 2100)
      month <- Gen.choose(1, 12)
      day   <- Gen.choose(1, 28)
    } yield {
      LocalDate.of(year, month, day)
    }
  }

  implicit val arbitraryStringNonEmptyWithoutNullChar: Arbitrary[String] = Arbitrary {
    Gen.identifier
  }

  implicit val arbitraryInetAddress: Arbitrary[InetAddress] = Arbitrary {
    val ipGen = Gen.choose(0, 250).map(_ + 1).map(_.toByte)
    Gen.containerOfN[Array, Byte](4, ipGen).map(InetAddress.getByAddress)
  }
}
