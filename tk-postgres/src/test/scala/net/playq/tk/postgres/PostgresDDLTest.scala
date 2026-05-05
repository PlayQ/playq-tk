package net.playq.tk.postgres

import distage.ModuleDef
import doobie.syntax.string.*
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.distage.testkit.model.TestConfig.PriorAxisDIKeys
import izumi.functional.bio.IO2
import izumi.reflect.TagKK
import net.playq.tk.postgres.PostgresDDLTest.{Ctx, Table1, Table2}
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.postgres.partitioning.model.TableName
import net.playq.tk.postgres.test.PostgresTestEnv
import net.playq.tk.test.TkTestBaseCtx
import zio.IO

final class PostgresDDLTest extends PostgresDDLTestBase[IO] with PostgresTestEnv[IO]

object PostgresDDLTest {
  final val TABLE2_NAME = "test_table2"

  final case class Ctx[F[+_, +_]](
    postgresConnector: PostgresConnector[F],
    t1: Table1,
    t2: Table2,
  )

  final class Table1 extends DDLComponent {
    val table: TableName   = TableName("public", "test_table1")
    override final val ddl = table.table ->
      sql"""
        drop table if exists ${RawSQL(TABLE2_NAME)};
        drop table if exists $table;
        create table if not exists $table (
          i integer not null primary key
        ) without oids;
        insert into $table values (1);
      """
  }

  final class Table2(table1: Table1) extends DDLComponent {
    val table: TableName   = TableName("public", TABLE2_NAME)
    override final val ddl = table.table ->
      sql"""
        create table if not exists $table (
          i integer not null primary key references ${table1.table} (i) on delete cascade
        ) without oids;
        insert into $table values (1);
      """
  }
}

abstract class PostgresDDLTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {

  // disable memoization to force component rerun
  override def memoizationRoots: PriorAxisDIKeys = Set.empty

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    DDLComponent.make[F, Table1]
    DDLComponent.make[F, Table2]
  }

  "Postgres connector Plugin" should {
    "perform DDL" in bound {
      ctx =>
        import ctx.postgresConnector
        val query = sql"select i from test_table2".query[Int].unique
        for {
          res <- postgresConnector.query("test-select")(query)
          _   <- assertIO(res == 1)
        } yield ()
    }
  }

}
