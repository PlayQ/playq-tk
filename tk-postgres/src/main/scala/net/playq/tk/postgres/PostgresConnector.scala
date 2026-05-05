package net.playq.tk.postgres

import distage.Id
import doobie.free.connection.ConnectionIO
import doobie.hikari.HikariTransactor
import izumi.functional.bio.Exit.{Error, Termination}
import izumi.functional.bio.{F, Panic2, Temporal2}
import logstage.LogIO2
import net.playq.tk.metrics.*
import net.playq.tk.postgres.config.PostgresConfig
import net.playq.tk.postgres.ddl.DDLComponent.DDLUpComponent
import net.playq.tk.postgres.exceptions.{SQLConnectorException, SQLQueryException, SQLTimeoutException}
import net.playq.tk.postgres.healthcheck.PostgresHealthChecker
import net.playq.tk.postgres.implicits.*
import net.playq.tk.quantified.MonadCancelThrowable

import java.net.URI
import java.sql.SQLTransientConnectionException
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.unused
import scala.concurrent.duration.*
import scala.util.chaining.*

trait PostgresConnector[F[_, _]] {

  def query[A](
    metaName: String
  )(query: ConnectionIO[A]
  )(implicit
    macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
    macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
    macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
  ): F[SQLConnectorException, A]

  def query[A](
    metaName: String,
    timeout: Duration,
  )(query: ConnectionIO[A]
  )(implicit
    macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
    macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
    macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
  ): F[SQLConnectorException, A]

  def roQuery[A](
    metaName: String
  )(query: ConnectionIO[A]
  )(implicit
    macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
    macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
    macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
  ): F[SQLConnectorException, A]

  def roQuery[A](
    metaName: String,
    timeout: Duration,
  )(query: ConnectionIO[A]
  )(implicit
    macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
    macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
    macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
  ): F[SQLConnectorException, A]
}

object PostgresConnector {
  final class Impl[F[+_, +_]: Panic2: Temporal2: MonadCancelThrowable](
    transactor: HikariTransactor[F[Throwable, _]],
    roTransactor: HikariTransactor[F[Throwable, _]] @Id("pg-readonly"),
    config: PostgresConfig,
    logger: LogIO2[F],
    metrics: Metrics[F],
    @unused postgresHealthChecker: PostgresHealthChecker[F],
    @unused ddlUpComponent: DDLUpComponent[F],
  ) extends PostgresConnector[F] {

    private val cc              = new AtomicInteger(0)
    private def debug(): String = {
      if (cc.incrementAndGet() % 10 == 0) {
        try {
          val host = URI.create(config.url).getHost
          import java.net.InetAddress
          InetAddress.getAllByName(host).toList.map(_.getHostAddress).mkString(";")
        } catch {
          case t: Throwable =>
            import izumi.fundamentals.platform.exceptions.IzThrowable.*
            t.stacktraceString
        }
      } else {
        s"cc=$cc"
      }
    }

    override def query[T](
      metaName: String
    )(query: ConnectionIO[T]
    )(implicit
      macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
      macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
      macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
    ): F[SQLConnectorException, T] = {
      this.query(metaName, config.defTimeout)(query)
    }

    override def query[T](
      metaName: String,
      timeout: Duration,
    )(query: ConnectionIO[T]
    )(implicit
      macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
      macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
      macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
    ): F[SQLConnectorException, T] = {
      queryImpl[T](metaName, timeout, transactor, query)
    }

    override def roQuery[T](
      metaName: String
    )(query: ConnectionIO[T]
    )(implicit
      macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
      macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
      macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
    ): F[SQLConnectorException, T] = {
      this.roQuery(metaName, config.defTimeout)(query)
    }

    override def roQuery[T](
      metaName: String,
      timeout: Duration,
    )(query: ConnectionIO[T]
    )(implicit
      macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
      macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
      macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
    ): F[SQLConnectorException, T] = {
      queryImpl[T](metaName, timeout, roTransactor, query)
    }

    private def queryImpl[T](
      metaName: String,
      timeout: Duration,
      txr: HikariTransactor[F[Throwable, _]],
      query: ConnectionIO[T],
    )(implicit
      macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
      macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
      macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
    ): F[SQLConnectorException, T] = {
      for {
        _   <- logger.debug(s"Performing query $metaName on ${debug()}")
        res <- metrics.timerAnd(metaName) {
          query
            .transact[F[Throwable, _]](txr)
            .pipe(catchDoobieDefects(metaName)(_))
            .timeout(timeout)
            .flatMap {
              case None =>
                metrics.mark(metaName)(using macroSaveMeterMetricTimeout) *>
                F.terminate(new SQLTimeoutException(s"Query $metaName timed out")): F[Nothing, T]
              case Some(res) =>
                F.pure(res)
            }
        }
      } yield res
    }

    // FIXME: shouldn't be necessary after 0.7? maybe remove on 0.8?
    private[this] def catchDoobieDefects[A](
      metaName: String
    )(f: F[Throwable, A]
    )(implicit
      macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type]
    ): F[SQLQueryException, A] = {
      f.sandbox.catchAll {
        case Termination(defect, _, trace) =>
          logger.error(s"Uncaught defect from doobie $metaName: $defect $trace") *>
          metrics.mark(metaName)(using macroSaveMeterMetricException) *>
          F.fail(new SQLQueryException(s"Query $metaName failed due to unhandled defect: $defect", defect, Some(trace.asString)))

        case Error(exc, trace) =>
          // here we don't need to log error as doobie log handler will log everything with convenient output
          metrics.mark(metaName)(using macroSaveMeterMetricException) *> {
            exc match {
              case e: SQLTransientConnectionException => F.terminate(new SQLQueryException(s"Couldn't connect to the db: $e", e, Some(trace.asString)))
              case e                                  => F.fail(new SQLQueryException(s"Query $metaName failed due to exception: $e", e, Some(trace.asString)))
            }
          }
      }
    }

  }

}
