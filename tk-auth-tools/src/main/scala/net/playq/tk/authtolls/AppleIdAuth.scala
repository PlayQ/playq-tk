package net.playq.tk.authtolls

import com.auth0.jwk.UrlJwkProvider
import izumi.functional.bio.{F, IO2}
import net.playq.tk.authtolls.AppleIdAuth.InvalidAppleId
import net.playq.tk.authtolls.models.AppleIdData
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtOptions}

import java.net.URL

final class AppleIdAuth[F[+_, +_]: IO2]() {
  private[this] val appleIss       = "https://appleid.apple.com"
  private[this] val appleKeyServer = "https://appleid.apple.com/auth/keys"
  private[this] val jwksProvider   = new UrlJwkProvider(new URL(appleKeyServer))
  def validate(token: String, appId: String): F[InvalidAppleId, AppleIdData] = {
    for {
      header <-
        F.fromTry(JwtCirce.decodeAll(token, JwtOptions(signature = false, expiration = false, notBefore = false)))
          .leftMap(f => InvalidAppleId(s"Can not decode AppleId token: ${f.getMessage}")).map(_._1)
      keyId <- F.fromOption(InvalidAppleId(s"Empty kid in provided jwt."))(header.keyId)
      jwk   <- F.syncThrowable(jwksProvider.get(keyId)).leftMap(f => InvalidAppleId(s"Can not fetch jwk by kid=$keyId: ${f.getMessage}"))
      claim <-
        F.fromTry(JwtCirce.decodeRaw(token, jwk.getPublicKey, JwtAlgorithm.allAsymmetric()))
          .leftMap(f => InvalidAppleId(s"Can not decode and verify AppleId token: ${f.getMessage}"))
      fromToken <- F.fromEither {
        io.circe.parser.parse(claim).flatMap {
          c =>
            for {
              aud           <- c.hcursor.downField("aud").as[String]
              iss           <- c.hcursor.downField("iss").as[String]
              sub           <- c.hcursor.downField("sub").as[String]
              email         <- c.hcursor.downField("email").as[Option[String]]
              emailVerified <- c.hcursor.downField("email_verified").as[Option[Boolean]]
            } yield (aud, iss, AppleIdData(sub, email, emailVerified))
        }
      }.leftMap(f => InvalidAppleId(s"Can not parse claim: $f"))
      (aud, iss, res) = fromToken
      _              <- F.when(aud != appId)(F.fail(InvalidAppleId(s"Unexpected audience.")))
      _              <- F.when(iss != appleIss)(F.fail(InvalidAppleId(s"Unexpected issuer.")))
    } yield res
  }
}

object AppleIdAuth {
  final case class InvalidAppleId(reason: String)
}
