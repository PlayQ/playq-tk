package net.playq.tk.aws.cost

import cats.effect.Ref
import cats.syntax.list.*
import izumi.functional.bio.{BlockingIO2, F, IO2, Temporal2}
import net.playq.tk.metrics.Metrics
import net.playq.tk.metrics.{MacroMetricCostMeter, MacroMetricCostTimer}
import net.playq.tk.quantified.SyncThrowable
import izumi.functional.bio.retry.{RetryPolicy, Scheduler2}
import software.amazon.awssdk.services.costexplorer.CostExplorerClient

import scala.jdk.CollectionConverters.*

trait CostClient[F[_, _]] {
  def getCosts(request: CostRequest): F[Throwable, CostResponse]

  private[cost] def rawRequestF[E, A](
    metric: String
  )(f: CostExplorerClient => F[E, A]
  )(implicit
    saveCounter: MacroMetricCostMeter[metric.type],
    saveTimer: MacroMetricCostTimer[metric.type],
  ): F[E, A]

  def rawRequest[A](
    metric: String
  )(f: CostExplorerClient => A
  )(implicit
    saveCounter: MacroMetricCostMeter[metric.type],
    saveTimer: MacroMetricCostTimer[metric.type],
  ): F[Throwable, A]
}

object CostClient {

  final class Impl[F[+_, +_]: IO2: SyncThrowable: BlockingIO2: Scheduler2](
    client: CostExplorerClient,
    metrics: Metrics[F],
  ) extends CostClient[F] {

    override def rawRequestF[E, A](
      metric: String
    )(f: CostExplorerClient => F[E, A]
    )(implicit
      saveCounter: MacroMetricCostMeter[metric.type],
      saveTimer: MacroMetricCostTimer[metric.type],
    ): F[E, A] = {
      F.shiftBlocking {
        metrics.timerAnd(metric) {
          f(client).catchAll {
            failure =>
              metrics.mark(metric)(using saveCounter) *> F.fail(failure)
          }
        }(using saveTimer)
      }
    }

    override def rawRequest[A](
      metric: String
    )(f: CostExplorerClient => A
    )(implicit
      saveCounter: MacroMetricCostMeter[metric.type],
      saveTimer: MacroMetricCostTimer[metric.type],
    ): F[Throwable, A] = {
      rawRequestF(metric)(c => F.syncThrowable(f(c)))
    }

    override def getCosts(request: CostRequest): F[Throwable, CostResponse] = {
      rawRequestF("get-costs") {
        client =>
          val schedule = RetryPolicy.recursWhile[F, Option[String]](_.nonEmpty)
          for {
            ref       <- Ref.of[F[Throwable, _], List[CostResponse]](Nil)
            nextToken <- Ref.of[F[Throwable, _], Option[String]](None)
            _         <- F.repeat {
              for {
                next   <- nextToken.get
                result <- F.syncThrowable(client.getCostAndUsage(request.withNextPageToken(next).makeRequest()))
                token   = Option(result.nextPageToken()).filter(_.nonEmpty)
                _      <- nextToken.set(token)
                res     = CostResponse(
                  groupDefinitions = Option(result.groupDefinitions()).map(_.asScala.map(CostGroupDefinition(_)).toSeq).getOrElse(Seq.empty),
                  resultsByTime    = result.resultsByTime().asScala.map(CostResultByTime(_)).toSeq,
                  nextPageToken    = result.nextPageToken(),
                )
                _ <- ref.update(res :: _)
              } yield token
            }(schedule)
            all <- ref.get
            res <- F.fromOption(new RuntimeException("Got empty result!")) {
              all.toNel.map(_.reduceLeft {
                (b1, b2) =>
                  b1.copy(resultsByTime = b2.resultsByTime ++ b1.resultsByTime)
              })
            }
          } yield res
      }
    }
  }
}
