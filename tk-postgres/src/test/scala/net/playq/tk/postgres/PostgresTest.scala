package net.playq.tk.postgres

import distage.TagK
import izumi.distage.model.definition.ModuleDef
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.IO2
import izumi.reflect.TagKK
import net.playq.tk.postgres.PostgresConnector
import net.playq.tk.postgres.docker.PostgresDockerDefault
import net.playq.tk.postgres.implicits.*
import net.playq.tk.postgres.test.PostgresTestEnv
import PostgresTest.Ctx
import net.playq.tk.test.TkTestBaseCtx
import zio.IO

final class PostgresTest extends PostgresTestBase[IO] with PostgresTestEnv[IO]

object PostgresTest {
  final class Ctx[F[+_, +_]](implicit val postgresConnector: PostgresConnector[F])
}

abstract class PostgresTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2](implicit ev: TagK[F[Throwable, _]]) extends TkTestBaseCtx[F, Ctx[F]] {

  "Postgres connector" should {
    "perform transactions" in scopeIO {
      ctx =>
        import ctx.*
        val s = "$acd"
        val q = sql"select 1, $s".logQuery[Int].unique
        postgresConnector.query("")(q).flatMap(i => assertIO(i == 1))
    }
  }

  override def moduleOverrides: distage.Module = super.moduleOverrides overriddenBy new ModuleDef {
    include(PostgresDockerDefault.module[F[Throwable, _]]("postgres"))
  }
}
