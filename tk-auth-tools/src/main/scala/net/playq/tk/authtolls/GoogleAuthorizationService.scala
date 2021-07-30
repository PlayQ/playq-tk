package net.playq.tk.authtolls

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import izumi.functional.bio.{Clock2, F, IO2}
import izumi.idealingua.runtime.circe.IRTWithCirce
import net.playq.tk.authtolls.GoogleAuthorizationService.{ExpiringAccessToken, GoogleAccessTokenMeta}
import net.playq.tk.http.TkHttp4sClient
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io.POST
import org.http4s.{EntityDecoder, Status, Uri, UrlForm}
import org.http4s.circe._
import net.playq.tk.quantified.SyncThrowable

trait GoogleAuthorizationService[F[_, _]] {
  def invalidateToken(key: String): F[Throwable, Unit]
  def accessToken(key: String, config: GoogleAccessTokenMeta): F[Throwable, ExpiringAccessToken]
}

object GoogleAuthorizationService {
  final class Impl[F[+_, +_]: IO2: SyncThrowable](
    httpClient: TkHttp4sClient[F],
    clientDsl: Http4sClientDsl[F[Throwable, ?]],
    clock: Clock2[F],
  ) extends GoogleAuthorizationService[F] {
    import clientDsl._

    private[this] val cache                                                                          = new java.util.concurrent.ConcurrentHashMap[String, ExpiringAccessToken]()
    private[this] implicit val accessTokenDecoder: EntityDecoder[F[Throwable, ?], GoogleAccessToken] = jsonOf[F[Throwable, ?], GoogleAccessToken]

    def invalidateToken(key: String): F[Nothing, Unit] = F.sync(cache.remove(key)).void

    def accessToken(key: String, config: GoogleAccessTokenMeta): F[Throwable, ExpiringAccessToken] = {
      for {
        existingToken <- F.sync(Option(cache.get(key)))
        token <- existingToken match {
          case Some(value) if value.isValid =>
            F.pure(value)
          case _ =>
            for {
              updatedToken <- refreshToken(config)
              _            <- F.sync(cache.put(key, updatedToken))
            } yield updatedToken
        }
      } yield token
    }

    private def refreshToken(config: GoogleAccessTokenMeta): F[Throwable, ExpiringAccessToken] = {
      val requestNode = UrlForm(
        "grant_type"    -> "refresh_token",
        "client_id"     -> config.clientId,
        "client_secret" -> config.clientSecret,
        "refresh_token" -> config.refreshToken,
      )

      for {
        req <- POST(requestNode, Uri.unsafeFromString(config.tokenRefreshUri))
        token <- httpClient.fetch(req) {
          case Status.Successful(r) =>
            r.as[GoogleAccessToken]
          case r =>
            r.as[String].flatMap {
              badBody =>
                F.fail(new GoogleTokenUpdateException(s"Request $req failed with status ${r.status.code} and body $badBody"))
            }
        }
        res <- Option(token.access_token) match {
          case Some(_) =>
            clock.now().map {
              now =>
                ExpiringAccessToken(token, now.truncatedTo(MILLIS))
            }: F[Throwable, ExpiringAccessToken]
          case None =>
            F.fail(new GoogleTokenUpdateException(s"Failed to authorize for Google: $token"))
        }
      } yield res
    }
  }

  final case class GoogleAccessToken(access_token: String, token_type: String, expires_in: Int)
  object GoogleAccessToken extends IRTWithCirce[GoogleAccessToken]

  final case class ExpiringAccessToken(token: GoogleAccessToken, issueTimestamp: ZonedDateTime) {
    def value: String = token.access_token
    def isValid: Boolean = {
      // we count token as oudated in two minutes before actual expiration time
      issueTimestamp.plusSeconds((token.expires_in - 120).toLong).isAfter(ZonedDateTime.now())
    }
  }

  final case class GoogleAccessTokenMeta(tokenRefreshUri: String, clientId: String, clientSecret: String, refreshToken: String)

  final class GoogleTokenUpdateException(message: String) extends IllegalStateException(message, null)
}
