package net.playq.tk.postgres

import doobie.free.connection.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.syntax.connectionio._
import doobie.util.log.LogHandler
import izumi.functional.bio.Exit.{Error, Interruption, Termination}
import izumi.functional.bio.{F, Panic2, Temporal2}
import logstage.LogIO2
import net.playq.metrics._
import net.playq.tk.metrics.{MacroMetricPostgresMeterException, MacroMetricPostgresMeterTimeout, MacroMetricPostgresTimer}
import net.playq.tk.postgres.config.PostgresConfig
import net.playq.tk.postgres.exceptions.{SQLConnectorException, SQLQueryException, SQLTimeoutException}
import net.playq.tk.postgres.healthcheck.PostgresHealthChecker
import net.playq.tk.postgres.syntax.TkDoobieLogHandler
import net.playq.tk.quantified.BracketThrowable

import java.net.URI
import java.sql.SQLTransientConnectionException
import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.unused
import scala.concurrent.duration._
import scala.util.chaining._

trait PostgresConnector[F[_, _]] {
  val logHandler: LogHandler

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
}

object PostgresConnector {
  final class Impl[F[+_, +_]: Panic2: Temporal2: BracketThrowable](
    transactor: HikariTransactor[F[Throwable, ?]],
    cfg: PostgresConfig,
    @unused postgresHealthChecker: PostgresHealthChecker[F],
    log: LogIO2[F],
    metrics: Metrics[F],
    tkDoobieLogHandler: TkDoobieLogHandler,
  ) extends PostgresConnector[F] {

    private val cc = new AtomicInteger(0)
    private def debug() = {
      if (cc.incrementAndGet() % 10 == 0) {
        try {
          val host = URI.create(cfg.url).getHost
          import java.net.InetAddress
          InetAddress.getAllByName(host).toList.map(_.getHostAddress).mkString(";")

        } catch {
          case t: Throwable =>
            import izumi.fundamentals.platform.exceptions.IzThrowable._
            t.stackTrace
        }
      } else {
        s"cc=$cc"
      }
    }

    override val logHandler: LogHandler = tkDoobieLogHandler.logHandler

    override def query[T](
      metaName: String
    )(query: ConnectionIO[T]
    )(implicit
      macroSaveTimerMetric: MacroMetricPostgresTimer[metaName.type],
      macroSaveMeterMetricException: MacroMetricPostgresMeterException[metaName.type],
      macroSaveMeterMetricTimeout: MacroMetricPostgresMeterTimeout[metaName.type],
    ): F[SQLConnectorException, T] = {
      this.query(metaName, cfg.defTimeout)(query)
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
      for {
        _ <- log.debug(s"Performing query $metaName on ${debug()}")
        res <- metrics.withTimer(metaName) {
          query
            .transact(transactor)
            .pipe(catchDoobieDefects(metaName)(_))
            .timeout(timeout)
            .flatMap {
              case None =>
                metrics.mark(metaName)(macroSaveMeterMetricTimeout) *>
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
          log.error(s"Uncaught defect from doobie $metaName: $defect $trace") *>
          metrics.mark(metaName)(macroSaveMeterMetricException) *>
          F.fail(new SQLQueryException(s"Query $metaName failed due to unhandled defect: $defect", defect, Some(trace.asString)))

        case Error(exc, trace) =>
          log.warn(s"Caught exception from doobie $metaName: $exc $trace") *>
          metrics.mark(metaName)(macroSaveMeterMetricException) *> {
            exc match {
              case e: SQLTransientConnectionException => F.terminate(new SQLQueryException(s"Couldn't connect to the db: $e", e, Some(trace.asString)))
              case e                                  => F.fail(new SQLQueryException(s"Query $metaName failed due to exception: $e", e, Some(trace.asString)))
            }
          }

        case Interruption(compoundException, trace) =>
          log.error(s"Interrupted while connecting to DB $metaName: $compoundException $trace") *>
          metrics.mark(metaName)(macroSaveMeterMetricException) *>
          F.fail(new SQLQueryException(s"Query $metaName failed due to interruption: $compoundException", compoundException, Some(trace.asString)))
      }
    }

  }

}
