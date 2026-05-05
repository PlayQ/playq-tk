package net.playq.tk.postgres

import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.IO2
import izumi.reflect.TagKK
import net.playq.tk.postgres.PostgresTest.Ctx
import net.playq.tk.postgres.implicits.*
import net.playq.tk.postgres.test.PostgresTestEnv
import net.playq.tk.test.TkTestBaseCtx
import zio.IO

final class PostgresTest extends PostgresTestBase[IO] with PostgresTestEnv[IO]

object PostgresTest {
  final class Ctx[F[+_, +_]](implicit val postgresConnector: PostgresConnector[F])
}

abstract class PostgresTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  "Postgres connector" should {
    "perform transactions" in bound {
      ctx =>
        import ctx.*
        val s = "$acd"
        val q = sql"select 1, $s".queryWithLabel[Int]("test").unique
        postgresConnector.query("test")(q).flatMap(i => assertIO(i == 1))
    }
  }
}
