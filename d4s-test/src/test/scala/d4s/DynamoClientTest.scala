package d4s

import d4s.DynamoClientTest.Ctx
import d4s.env.DynamoTestBase
import net.playq.tk.test.rnd.TkRndBase
import zio.IO

object DynamoClientTest {
  final case class Ctx(dynamo: DynamoClient[IO])
}

final class DynamoClientTest extends DynamoTestBase[Ctx] with TkRndBase {
  "list tables" in scopeIO {
    ctx =>
      import ctx.*
      for {
        response <- dynamo.raw(_.listTables())
        _        <- assertIO(response != null)
        _        <- assertIO(response.tableNames != null)
      } yield ()
  }
}
