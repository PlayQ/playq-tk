package net.playq.tk.authtolls

import net.playq.tk.authtolls.models.GoogleUserInfo
import net.playq.tk.http.TkHttp4sClient
import net.playq.tk.quantified.ConcurrentThrowable
import org.http4s.*
import org.http4s.headers.Authorization

final class GoogleOAuth[F[+_, +_]: ConcurrentThrowable](
  client: TkHttp4sClient[F]
) {
  private[this] val uri                                     = Uri.unsafeFromString("https://www.googleapis.com/oauth2/v3/userinfo")
  def validate(token: String): F[Throwable, GoogleUserInfo] = {
    val request = Request[F[Throwable, _]](Method.GET, uri, headers = Headers(Authorization(Credentials.Token(AuthScheme.Bearer, token))))
    client.expect[GoogleUserInfo](request)
  }
}
