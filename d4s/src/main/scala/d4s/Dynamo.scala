package d4s

import d4s.Dynamo.{DynamoMetric, DynamoQueryExecutor, DynamoStreamExecutor}
import d4s.config.{DynamoBatchConfig, DynamoConfig, DynamoTransactionConfig}
import d4s.exceptions.DynamoException
import d4s.exceptions.DynamoException.QueryException
import d4s.query.effect.{QueryCtx, QueryLogger, QueryStreamLogger}
import d4s.query.{DynamoQuery, DynamoStream, IsSafeQuery}
import d4s.services.{DynamoClient, DynamoHealthChecker, DynamoSetupService}
import fs2.Stream
import izumi.functional.bio.data.Morphism1
import izumi.functional.bio.{Async2, Temporal2}
import logstage.LogIO2
import net.playq.tk.metrics.{MacroMetricDynamoMeter, MacroMetricDynamoTimer, Metrics}
import net.playq.tk.quantified.ConcurrentThrowable

import scala.annotation.unused
import scala.language.implicitConversions

/** Dynamo query/stream execution entrypoint.
  *
  * {{{
  *   final class MyItem(key1: String, key2: String, v: Int)
  *   final class MyTable(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
  *     override val name: String                   = "table1"
  *     override val serviceName: ServiceName       = ServiceName("my-service")
  *     override val key: DynamoKey[String, String] = DynamoKey("key1", "key2")
  *     override val ttlField: Option[String]       = None
  *   }
  *
  *   val table = MyTable(implicitly)
  *   val tablesService: DynamoTablesService[F] = ???
  *   val dynamo: Dynamo[F] = ???
  *
  *   for {
  *     _ <- tablesService.setup(table)
  *     _ <- dynamo.run {
  *       table.putItem(MyItem("1", "2", 1))
  *     }
  *     _ <- dynamo.run {
  *       table.getItem(table.key.bind("1"))
  *     }
  *     _ <- dynamo.stream("my-stream") {
  *       table.scan.stream
  *     }.compile.drain
  *   } yield ()
  * }}}
  *
  * @see [[d4s.modules.D4SModule]] - distage recipe for building a [[d4s.Dynamo]] instance.
  */
trait Dynamo[F[_, _]] {
  /** Execute non-metered query:
    * {{{
    *   dynamo.run {
    *     table.putItem(item)
    *   }
    * }}}
    */
  def run: DynamoQueryExecutor[F]

  /** Execute metered query:
    * {{{
    *   dynamo.run("my-metric-name") {
    *     table.putItem(item)
    *   }
    * }}}
    */
  def run(metric: DynamoMetric): DynamoQueryExecutor[F]

  /** Stream non-metered query:
    * {{{
    *   connector.stream {
    *     table.scan.stream
    *   }
    * }}}
    */
  def stream: DynamoStreamExecutor[F]

  /** Stream metered query:
    * {{{
    *   connector.stream("my-metric-name") {
    *     table.scan.stream
    *   }
    * }}}
    */
  def stream(metric: DynamoMetric): DynamoStreamExecutor[F]
}

object Dynamo {
  /** Dynamo-labeled metrics wrapper.
    * Needed to wrap user-defined label with:
    *  - Timer: "label" -> "dynamo/label/timer"
    *  - Error Meter: "label" -> "dynamo/request-error/label"
    */
  sealed trait DynamoMetric {
    def timerAnd[F[_, _], E, T](metrics: Metrics[F])(f: => F[E, T]): F[E, T]
    def mark[F[_, _]](metrics: Metrics[F]): F[Nothing, Unit]
  }
  object DynamoMetric {
    implicit def from(
      label: String
    )(implicit
      timer: MacroMetricDynamoTimer[label.type],
      meter: MacroMetricDynamoMeter[label.type],
    ): DynamoMetric = new DynamoMetric {
      override def timerAnd[F[_, _], E, T](metrics: Metrics[F])(f: => F[E, T]): F[E, T] = metrics.timerAnd(label)(f)
      override def mark[F[_, _]](metrics: Metrics[F]): F[Nothing, Unit]                 = metrics.mark(label)
    }
  }

  trait DynamoQueryExecutor[F[_, _]] {
    def apply[DR[+d] <: DynamoQuery[DR, d]: IsSafeQuery, A](q: DR[A]): F[QueryException, A]
  }

  trait DynamoStreamExecutor[F[_, _]] {
    def apply[DR[+d] <: DynamoStream[DR, d], A](q: DR[A]): Stream[F[QueryException, _], A]
  }

  final class Impl[F[+_, +_]: Async2: Temporal2: ConcurrentThrowable](
    dynamoClient: DynamoClient[F],
    batchConfig: DynamoBatchConfig,
    transactConfig: DynamoTransactionConfig,
    dynamoConfig: DynamoConfig,
    metrics: Metrics[F],
    log: LogIO2[F],
    @unused healthChecker: DynamoHealthChecker[F],
    @unused setupService: DynamoSetupService[F],
  ) extends Dynamo[F] {

    override def run: DynamoQueryExecutor[F] = new DynamoQueryExecutor[F] {
      override def apply[DR[+d] <: DynamoQuery[DR, d]: IsSafeQuery, A](q: DR[A]): F[QueryException, A] = {
        runImpl(q).leftMap(QueryException(_))
      }
    }

    override def run(metric: DynamoMetric): DynamoQueryExecutor[F] = new DynamoQueryExecutor[F] {
      override def apply[DR[+d] <: DynamoQuery[DR, d]: IsSafeQuery, A](q: DR[A]): F[QueryException, A] = {
        recordMetrics(metric)(runImpl(q)).leftMap(QueryException(_))
      }
    }

    override def stream: DynamoStreamExecutor[F] = new DynamoStreamExecutor[F] {
      override def apply[DR[+d] <: DynamoStream[DR, d], A](q: DR[A]): Stream[F[QueryException, _], A] = {
        runStreamImpl(q)
      }
    }

    override def stream(metric: DynamoMetric): DynamoStreamExecutor[F] = new DynamoStreamExecutor[F] {
      override def apply[DR[+d] <: DynamoStream[DR, d], A](q: DR[A]): Stream[F[QueryException, _], A] = {
        runStreamImpl(q, metricsStreamLogger(metric))
      }
    }

    private[this] def runImpl[DR[+d] <: DynamoQuery[DR, d], A](q: DR[A]): F[DynamoException, A] = {
      q.execute(mkContext(), QueryLogger.default)
    }

    private[this] def runStreamImpl[DR[+d] <: DynamoStream[DR, d], A](
      q: DR[A],
      streamLogger: QueryStreamLogger[F] = QueryStreamLogger.default,
    ): Stream[F[QueryException, _], A] = {
      q.execute(mkContext(), streamLogger).translate {
        Morphism1[F[Throwable, _], F[QueryException, _]](_.leftMap(QueryException(_))).toCats
      }
    }

    private[this] def mkContext(): QueryCtx[F] = {
      new QueryCtx(dynamoClient, log, batchConfig, transactConfig, dynamoConfig)
    }

    private[this] def metricsStreamLogger(metric: DynamoMetric): QueryStreamLogger[F] = new QueryStreamLogger[F] {
      override def wrap[E <: Throwable, A](ctx: QueryCtx[F])(f: F[E, A]): F[E, A] = {
        QueryLogger.default.withLogger(ctx.logger) {
          recordMetrics(metric)(f)
        }
      }
    }

    private[this] def recordMetrics[S <: String, A, E <: Throwable](metric: DynamoMetric)(f: F[E, A]): F[E, A] = {
      metric.timerAnd(metrics) {
        f.tapError {
          exception =>
            metric.mark(metrics) *>
            metrics.mark("dynamo/query-exception") *>
            log.error(s"Uncaught DynamoDB Exception $exception")
        }
      }
    }
  }
}
