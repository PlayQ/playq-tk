package d4s

import cats.~>
import d4s.health.DynamoDBHealthChecker
import d4s.models.DynamoException.QueryException
import d4s.models.ExecutionStrategy.StrategyInput
import d4s.models.query.DynamoRequest
import d4s.models.{DynamoException, DynamoExecution}
import fs2.Stream
import izumi.functional.bio.{Async2, Temporal2}
import izumi.fundamentals.platform.language.unused
import logstage.LogIO2
import net.playq.tk.metrics.{MacroMetricDynamoMeter, MacroMetricDynamoTimer, Metrics}

trait DynamoConnector[F[_, _]] {
  def runUnrecorded[DR <: DynamoRequest, A](q: DynamoExecution[DR, ?, A]): F[DynamoException, A]
  def runUnrecorded[DR <: DynamoRequest, A](q: DynamoExecution.Streamed[DR, ?, A]): Stream[F[DynamoException, _], A]

  def run[DR <: DynamoRequest, Dec, A](
    label: String
  )(q: DynamoExecution[DR, Dec, A]
  )(implicit
    macroTimeSaver: MacroMetricDynamoTimer[label.type],
    macroMeterSaver: MacroMetricDynamoMeter[label.type],
  ): F[DynamoException, A]

  def runStreamed[DR <: DynamoRequest, Dec, A](
    label: String
  )(q: DynamoExecution.Streamed[DR, Dec, A]
  )(implicit
    macroTimeSaver: MacroMetricDynamoTimer[label.type],
    macroMeterSaver: MacroMetricDynamoMeter[label.type],
  ): Stream[F[DynamoException, _], A]
}

object DynamoConnector {
  final class Impl[F[+_, +_]: Async2: Temporal2](
    interpreter: DynamoInterpreter[F],
    @unused dynamoDBHealthChecker: DynamoDBHealthChecker[F],
    @unused dynamoDDLService: DynamoDDLService[F],
    metrics: Metrics[F],
    log: LogIO2[F],
  ) extends DynamoConnector[F] {

    override def runUnrecorded[DR <: DynamoRequest, A](q: DynamoExecution[DR, ?, A]): F[DynamoException, A] =
      runUnrecordedImpl(q).leftMap(QueryException(_))

    override def runUnrecorded[DR <: DynamoRequest, A](q: DynamoExecution.Streamed[DR, ?, A]): Stream[F[DynamoException, _], A] =
      runUnrecordedImpl(q).translate(Lambda[F[Throwable, _] ~> F[DynamoException, _]](_.leftMap(QueryException(_))))

    private[this] def runUnrecordedImpl[DR <: DynamoRequest, Dec, Out[_[+_, +_]]](q: DynamoExecution.Dependent[DR, Dec, Out]): Out[F] = {
      q.executionStrategy(StrategyInput(q.dynamoQuery, interpreter))
    }

    override def run[DR <: DynamoRequest, Dec, A](
      label: String
    )(q: DynamoExecution[DR, Dec, A]
    )(implicit
      macroTimeSaver: MacroMetricDynamoTimer[label.type],
      macroMeterSaver: MacroMetricDynamoMeter[label.type],
    ): F[DynamoException, A] = {
      recordMetrics(label) {
        runUnrecorded(q)
      }.leftMap(QueryException(label, _))
    }

    override def runStreamed[DR <: DynamoRequest, Dec, A](
      label: String
    )(q: DynamoExecution.Streamed[DR, Dec, A]
    )(implicit
      macroTimeSaver: MacroMetricDynamoTimer[label.type],
      macroMeterSaver: MacroMetricDynamoMeter[label.type],
    ): Stream[F[DynamoException, _], A] = {
      val recordStreamPageMetrics = Lambda[F[Throwable, _] ~> F[Throwable, _]](recordMetrics(label)(_))

      q.executionStrategy(StrategyInput(q.dynamoQuery, interpreter, streamExecutionWrapper = recordStreamPageMetrics))
        .translate(Lambda[F[Throwable, _] ~> F[DynamoException, _]](_.leftMap(QueryException(label, _))))
    }

    private[this] def recordMetrics[A](
      label: String
    )(f: F[Throwable, A]
    )(implicit
      macroTimeSaver: MacroMetricDynamoTimer[label.type],
      macroMeterSaver: MacroMetricDynamoMeter[label.type],
    ): F[Throwable, A] = {
      metrics.withTimer(label) {
        f.tapError {
          exception =>
            metrics.mark(label) *>
            metrics.mark("dynamo/query-exception") *>
            log.error(s"Uncaught DynamoDB Exception $exception")
        }
      }
    }

  }

}
