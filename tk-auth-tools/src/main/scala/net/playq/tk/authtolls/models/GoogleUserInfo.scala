package net.playq.tk.authtolls.models

import io.circe.Decoder
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf
import net.playq.tk.quantified.SyncThrowable

final case class GoogleUserInfo(userId: Option[String], name: Option[String], email: String, emailVerified: Boolean, locale: Option[String])

object GoogleUserInfo {
  implicit val decoder: Decoder[GoogleUserInfo] =
    Decoder.forProduct5[GoogleUserInfo, Option[String], Option[String], String, Boolean, Option[String]]("sub", "name", "email", "email_verified", "locale") {
      GoogleUserInfo.apply
    }

  implicit def entityEncoder[F[+_, +_]: SyncThrowable]: EntityDecoder[F[Throwable, ?], GoogleUserInfo] = {
    jsonOf[F[Throwable, ?], GoogleUserInfo]
  }
}
