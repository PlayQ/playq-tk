package net.playq.tk.authtolls

import com.auth0.jwk.{Jwk, UrlJwkProvider}
import io.circe.Json
import izumi.functional.bio.{F, IO2}
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.authtolls.AppleIdAuth.InvalidAppleId
import net.playq.tk.authtolls.models.AppleIdData
import net.playq.tk.cache.{CacheBase, CacheConfig}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtOptions}

import java.net.URI
import scala.concurrent.duration.DurationInt

// https://developer.apple.com/documentation/sign_in_with_apple/sign_in_with_apple_rest_api/verifying_a_user
final class AppleIdAuth[F[+_, +_]: IO2]() {
  private[this] val appleIss                                            = "https://appleid.apple.com"
  private[this] val appleKeyServer                                      = "https://appleid.apple.com/auth/keys"
  private[this] val jwksKeysCache: CacheBase[F, Throwable, String, Jwk] = {
    new CacheBase[F, Throwable, String, Jwk](CacheConfig(100, 5.minute)) {
      private[this] val jwksProvider                              = new UrlJwkProvider(new URI(appleKeyServer).toURL)
      override protected def load(key: String): F[Throwable, Jwk] = F.syncThrowable(jwksProvider.get(key))
    }
  }
  def validate(audiences: NEList[String], token: String): F[InvalidAppleId, AppleIdData] = {
    for {
      header <- F
        .fromTry(JwtCirce.decodeAll(token, JwtOptions(signature = false, expiration = false, notBefore = false)))
        .leftMap(f => InvalidAppleId(s"Can not decode AppleId token: ${f.getMessage}")).map(_._1)
      keyId <- F.fromOption(InvalidAppleId(s"Empty kid in provided jwt."))(header.keyId)
      jwk   <- jwksKeysCache.get(keyId).leftMap(f => InvalidAppleId(s"Can not fetch jwk by kid=$keyId: ${f.getMessage}"))
      claim <- F
        .fromTry(JwtCirce.decodeRaw(token, jwk.getPublicKey, JwtAlgorithm.allAsymmetric()))
        .leftMap(f => InvalidAppleId(s"Can not decode and verify AppleId token: ${f.getMessage}"))
      fromToken <- F.fromEither {
        io.circe.parser.parse(claim).flatMap {
          c =>
            for {
              aud           <- c.hcursor.downField("aud").as[String]
              iss           <- c.hcursor.downField("iss").as[String]
              sub           <- c.hcursor.downField("sub").as[String]
              email         <- c.hcursor.downField("email").as[Option[String]]
              emailVerified <- c.hcursor.downField("email_verified").as[Json].flatMap {
                j =>
                  j.as[Option[Boolean]].orElse {
                    // trying to cast from string
                    j.as[Option[String]].map(_.map {
                      case "true" => true
                      case _      => false
                    })
                  }
              }
            } yield (aud, iss, AppleIdData(sub, email, emailVerified))
        }
      }.leftMap(f => InvalidAppleId(s"Can not parse claim: $f"))
      (aud, iss, res) = fromToken
      _              <- F.when(!audiences.toSet.contains(aud))(F.fail(InvalidAppleId(s"Unexpected audience.")))
      _              <- F.when(iss != appleIss)(F.fail(InvalidAppleId(s"Unexpected issuer.")))
    } yield res
  }
}

object AppleIdAuth {
  final case class InvalidAppleId(reason: String)
}
