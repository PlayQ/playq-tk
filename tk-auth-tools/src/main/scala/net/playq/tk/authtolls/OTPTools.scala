package net.playq.tk.authtolls

import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil
import izumi.functional.bio.{F, IO2}

import java.security.SecureRandom
import java.time.Instant

trait OTPTools[F[_, _]] {
  def generateSecret(length: Int                                      = 64): F[Nothing, String]
  def generateTOTP(secret: String, now: Instant, numbers: Int         = 6): F[Nothing, String]
  def generateTOTPsWithDelta(secret: String, now: Instant, delta: Int = 1, numbers: Int = 6): F[Nothing, Set[String]]
  def generateOTP(numbers: Int                                        = 6): F[Nothing, String]

  final def TOTPQRLink(issuer: String, email: String, secret: String): String = {
    s"otpauth://totp/$issuer:$email?secret=$secret&issuer=$issuer".replace(" ", "-")
  }
}

object OTPTools {
  final class Impl[F[+_, +_]: IO2] extends OTPTools[F] {
    private[this] val totpTimeStepSeconds: Int                   = 30
    override def generateSecret(length: Int): F[Nothing, String] = F.sync {
      TimeBasedOneTimePasswordUtil.generateBase32Secret(length)
    }
    override def generateTOTP(secret: String, now: Instant, numbers: Int): F[Nothing, String] = F.sync {
      TimeBasedOneTimePasswordUtil.generateNumberString(secret, now.toEpochMilli, totpTimeStepSeconds, numbers)
    }
    override def generateTOTPsWithDelta(secret: String, now: Instant, delta: Int, numbers: Int): F[Nothing, Set[String]] = F.sync {
      val epochMilli = now.toEpochMilli
      Iterator
        .range(-delta, delta + 1)
        .map(delta => epochMilli + (delta * totpTimeStepSeconds))
        .map(at => TimeBasedOneTimePasswordUtil.generateNumberString(secret, at, totpTimeStepSeconds, numbers))
        .toSet
    }
    override def generateOTP(numbers: Int): F[Nothing, String] = F.sync {
      val random = new SecureRandom()
      (0 until numbers).map(_ => random.nextInt(9)).mkString("")
    }
  }
}
