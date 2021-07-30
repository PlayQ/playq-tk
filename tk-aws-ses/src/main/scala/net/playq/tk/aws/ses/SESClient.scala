package net.playq.tk.aws.ses

import izumi.functional.bio.{Applicative2, F, IO2}
import net.playq.tk.aws.ses.config.SESConfig

import scala.jdk.CollectionConverters._

trait SESClient[F[_, _]] {
  def sendEmail(email: SESEmail): F[Throwable, Unit]
}

object SESClient {
  final class Dummy[F[+_, +_]: Applicative2] extends SESClient[F] {
    override def sendEmail(email: SESEmail): F[Throwable, Unit] = F.unit
  }

  final class Impl[F[+_, +_]: IO2](
    sesComponent: SESComponent[F],
    sesConfig: SESConfig,
  ) extends SESClient[F] {

    private val simpleEmailPattern = """\A([^@\s]+)@((?:[-a-z0-9]+\.)+[a-z]{2,})\z""".r

    override def sendEmail(email: SESEmail): F[Throwable, Unit] = {
      val request = email.cook(sesConfig)
      for {
        _ <- F.traverse_(request.destination.toAddresses.asScala)(validateEmail)
        _ <- sesComponent.makeRequest(_.sendEmail(request))
      } yield ()
    }

    private[this] def validateEmail(email: String): F[EmailValidationError, Unit] = {
      F.when(!email.matches(simpleEmailPattern.regex)) {
        F.fail(EmailValidationError(s"Given email: $email is invalid."))
      }
    }
  }

  final case class EmailValidationError(message: String) extends RuntimeException(message)
}
