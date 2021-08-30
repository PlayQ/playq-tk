package net.playq.tk.postgres

import distage.{ModuleDef, TagK}
import doobie.syntax.string.*
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.distage.testkit.TestConfig.PriorAxisDIKeys
import izumi.functional.bio.IO2
import izumi.reflect.TagKK
import net.playq.tk.postgres.PostgresConnector
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.postgres.ddl.DDLComponent.DDLUpComponent
import net.playq.tk.postgres.docker.PostgresDockerDefault
import net.playq.tk.postgres.test.PostgresTestEnv
import PostgresDDLTest.Ctx
import net.playq.tk.test.TkTestBaseCtx
import zio.IO

final class PostgresDDLTest extends PostgresTestBase[IO] with PostgresTestEnv[IO]

object PostgresDDLTest {
  final case class Ctx[F[+_, +_]](
    postgresConnector: PostgresConnector[F],
    DDLUpComponent: DDLUpComponent[F],
  )
}

abstract class PostgresDDLTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2](implicit ev: TagK[F[Throwable, _]]) extends TkTestBaseCtx[F, Ctx[F]] {

  // disable memoization to force component rerun
  override def memoizationRoots: PriorAxisDIKeys = Set.empty

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    many[DDLComponent].add {
      new DDLComponent {
        override final val ddl = "test-table" ->
          sql"""
            drop table if exists test_table;
            create table if not exists test_table (
              i integer not null
            ) without oids;
            insert into test_table values (1);
          """
      }
    }
    include(PostgresDockerDefault.module[F[Throwable, _]]("postgres"))
  }

  "Postgres connector Plugin" should {
    "perform DDL" in scopeIO {
      ctx =>
        import ctx.postgresConnector
        val query = sql"select * from test_table".query[Int].unique
        for {
          res <- postgresConnector.query("test-select")(query)
          _   <- assertIO(res == 1)
        } yield ()
    }
  }

}
