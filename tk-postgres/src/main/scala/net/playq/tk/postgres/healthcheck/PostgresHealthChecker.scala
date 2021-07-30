package net.playq.tk.postgres.healthcheck

import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import izumi.functional.bio.{F, Panic2}
import net.playq.tk.health
import net.playq.tk.health.{HealthChecker, TgHealthCheckStatus, TgHealthState}
import net.playq.tk.postgres.healthcheck.PostgresHealthChecker._
import net.playq.tk.postgres.partitioning.model.TableName
import net.playq.tk.quantified.BracketThrowable

// TODO: cyclic deps conflict if it will depends on [[PostgresConnector]]
final case class PostgresCheckTable(tables: Set[TableName])
object PostgresCheckTable {
  def apply(s: (String, String)*): PostgresCheckTable = PostgresCheckTable(s.map { case (sc, t) => TableName(sc, t) }.toSet)
}

final class PostgresHealthChecker[F[+_, +_]: Panic2: BracketThrowable](
  transactor: HikariTransactor[F[Throwable, ?]],
  tablesChecks: Set[PostgresCheckTable],
) extends HealthChecker[F] {

  override def healthCheck(): F[Throwable, Set[TgHealthCheckStatus]] = {
    for {
      s <- sessionHealthCheck
      t <- tablesHealthCheck
    } yield s ++ t
  }

  private[this] val schemaTable: Set[TableName] = tablesChecks.flatMap(_.tables)
  private[this] val queries: Set[NameQuery] = schemaTable.map {
    st =>
      val q = sql"""SELECT EXISTS (
          SELECT 1
          FROM   pg_catalog.pg_class c
          JOIN   pg_catalog.pg_namespace n ON n.oid = c.relnamespace
          WHERE  n.nspname = ${st.schema}
          AND    c.relname = ${st.table}
          AND    c.relkind = 'r'
        );""".query[Res]
      val name = s"postgres.table.alive.${st.schema}.${st.table}"
      NameQuery(name, q)
  }

  private[this] def tablesHealthCheck: F[Throwable, Set[TgHealthCheckStatus]] = {
    F.traverse(queries) {
      q =>
        status(q.query).map(health.TgHealthCheckStatus(q.name, _))
    }.map(_.toSet)
  }

  private[this] def sessionHealthCheck: F[Throwable, Set[TgHealthCheckStatus]] = {
    status(PostgresHealthChecker.schemaExsistsStmt).map {
      st =>
        Set(health.TgHealthCheckStatus("postgres.session", st))
    }
  }

  private[this] def status(query: Query0[Res]): F[Nothing, TgHealthState] = {
    query.nel.map(_.head).transact(transactor).sandbox.attempt.map {
      case Right(PostgresHealthChecker.Res(true)) =>
        TgHealthState.OK
      case _ =>
        TgHealthState.DEFUNCT
    }
  }
}

object PostgresHealthChecker {
  private final case class Res(exists: Boolean)

  private final case class NameQuery(name: String, query: Query0[Res])

  private val schemaExsistsStmt: Query0[Res] = {
    sql"""SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname = 'public');""".query[Res]
  }
}
