package d4s.services

import d4s.services.DynamoClientTest.Ctx
import d4s.test.DynamoTestBase
import distage.{DefaultModule2, TagKK}
import izumi.functional.bio.IO2
import net.playq.tk.test.rnd.TkRndBase
import zio.IO

final class DynamoClientTest extends DynamoClientTestBase[IO]

object DynamoClientTest {
  final case class Ctx[F[+_, +_]](
    dynamo: DynamoClient[F]
  )
}

abstract class DynamoClientTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends DynamoTestBase[F, Ctx[F]] with TkRndBase {
  "DynamoClient" should {
    "support base ops" in bound {
      ctx =>
        import ctx.*
        for {
          response <- dynamo.executeFuture(_.listTables())
          _        <- assertIO(response != null)
          _        <- assertIO(response.tableNames != null)
        } yield ()
    }
  }
}
