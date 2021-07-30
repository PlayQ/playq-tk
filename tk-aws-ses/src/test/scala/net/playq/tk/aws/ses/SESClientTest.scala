package net.playq.tk.aws.ses

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.Applicative2
import SESClientTest.Ctx
import net.playq.tk.aws.ses.test.SESTestEnv
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import net.playq.tk.test.rnd.TkRndGeneric
import zio.IO

final class SESClientTest extends SESClientTestBase[IO] with WithProduction with SESTestEnv

object SESClientTest {
  final case class Ctx[F[+_, +_]](
    sesClient: SESClient[F]
  )
}

abstract class SESClientTestBase[F[+_, +_]: Applicative2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] with TkRndGeneric {

  "SES client" should {
    "Send email to end user" in scopeIO {
      ctx =>
        import ctx._
        val emailBody = """<h1>Category Theory for Kittens</h1>
                          |<p>This is an invitation to an incredible journey into the WORLD OF FP.
                          |Click on the link to start it right now!!!.""".stripMargin

        val txtBody = "Hello, Kitty."

        val request = SESEmail("test@gmail.com")
          .withHtml(emailBody)
          .withTxt(txtBody)
          .withSubject("Reset password")

        for {
          _ <- sesClient.sendEmail(request)
        } yield ()
    }
  }

}
