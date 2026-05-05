package net.playq.tk.loadtool.scenario

import izumi.functional.bio.{Clock2, Exit, F, IO2, Primitives2, Ref2}
import izumi.fundamentals.platform.strings.IzString.*
import logstage.LogIO2
import net.playq.tk.loadtool.scenario.ScenarioContext.ExecutionReport
import net.playq.tk.loadtool.scenario.ScenarioContext.ExecutionReport.Stats

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

trait ScenarioContext[F[_, _]] {
  val logger: LogIO2[F]
  val clock: Clock2[F]

  def logReport: F[Nothing, Unit]
  def report: F[Nothing, ExecutionReport]

  private[loadtool] def wrap[E, A](label: String)(exec: F[E, A]): F[E, A]
  private[loadtool] def executed: F[Nothing, Long]

  private[loadtool] def fail: F[Nothing, Long]

  private[loadtool] def stepFailed(label: String): F[Nothing, Unit]
  private[loadtool] def stepSuccess(label: String, start: ZonedDateTime, end: ZonedDateTime): F[Nothing, Unit]
}

object ScenarioContext {

  def apply[F[+_, +_]: IO2: Primitives2](
    logger: LogIO2[F],
    clock: Clock2[F],
  ): F[Nothing, ScenarioContext[F]] = {
    for {
      stepTimingsRef     <- F.mkRef(Map.empty[String, Long])
      stepExecutionsRef  <- F.mkRef(Map.empty[String, Long])
      stepFailingsRef    <- F.mkRef(Map.empty[String, Long])
      totalExecutionsRef <- F.mkRef(0L)
      totalFailsRef      <- F.mkRef(0L)
    } yield new Default(logger, clock, stepTimingsRef, stepExecutionsRef, stepFailingsRef, totalExecutionsRef, totalFailsRef)
  }

  final class Default[F[+_, +_]: IO2] private[ScenarioContext] (
    val logger: LogIO2[F],
    val clock: Clock2[F],
    // step metrics
    private[this] val stepTimingsRef: Ref2[F, Map[String, Long]],
    private[this] val stepExecutionsRef: Ref2[F, Map[String, Long]],
    private[this] val stepFailingsRef: Ref2[F, Map[String, Long]],
    // shared metrics
    private[this] val totalExecutionsRef: Ref2[F, Long],
    private[this] val totalFailsRef: Ref2[F, Long],
  ) extends ScenarioContext[F] {

    def logReport: F[Nothing, Unit] = {
      report.flatMap {
        case stats: ExecutionReport.Stats =>
          logger.info {
            s"""Scenario report:
               |${stats.totalExecutions -> "Executions"};
               |${stats.totalFails -> "Fails"};
               |${stats.stepTimings.map { case (k, v) => k -> s"$v ms" }.niceList() -> "Steps AVG timings"};
               |${stats.stepExecutions.niceList() -> "Steps executions"};
               |${stats.stepFailings.niceList() -> "Steps failings"}.
               |""".stripMargin
          }
        case ExecutionReport.ExecutionFailure(err) =>
          logger.error(s"Scenario failed $err.")
      }
    }

    def report: F[Nothing, ExecutionReport] = {
      for {
        totalFails <- totalFailsRef.get
        totalRuns  <- totalExecutionsRef.get

        totalTimings <- stepTimingsRef.get
        executions   <- stepExecutionsRef.get
        timings       = totalTimings.flatMap { case (id, t) => executions.get(id).map(num => id -> (t.toDouble / num.toDouble).toLong) }
        fails        <- stepFailingsRef.get
      } yield Stats(totalRuns, totalFails, timings, executions, fails)
    }

    private[loadtool] def wrap[E, A](label: String)(exec: F[E, A]): F[E, A] = {
      for {
        _     <- logger.debug(s"Executing scenario step $label")
        start <- clock.nowZoned()
        ex    <- exec.sandboxExit
        end   <- clock.nowZoned()
        res   <- ex match {
          case Exit.Success(v)             => stepSuccess(label, start, end) *> F.pure(v)
          case Exit.Error(err, _)          => stepFailed(label) *> F.fail(err)
          case Exit.Termination(err, _, _) => stepFailed(label) *> F.terminate(err)
        }
      } yield res
    }

    private[loadtool] def executed: F[Nothing, Long] = totalExecutionsRef.update(_ + 1L)

    private[loadtool] def fail: F[Nothing, Long] = totalFailsRef.update(_ + 1L)

    private[loadtool] def stepFailed(label: String): F[Nothing, Unit] = {
      for {
        _ <- stepFailingsRef.update(_.updatedWith(label)(_.map(_ + 1L).orElse(Some(1L))))
        _ <- stepExecutionsRef.update(_.updatedWith(label)(_.map(_ + 1L).orElse(Some(1L))))
      } yield ()
    }

    private[loadtool] def stepSuccess(label: String, start: ZonedDateTime, end: ZonedDateTime): F[Nothing, Unit] = {
      val millis = ChronoUnit.MILLIS.between(start, end)
      for {
        _ <- stepTimingsRef.update(_.updatedWith(label)(_.map(_ + millis).orElse(Some(millis))))
        _ <- stepExecutionsRef.update(_.updatedWith(label)(_.map(_ + 1L).orElse(Some(1L))))
      } yield ()
    }
  }

  sealed trait ExecutionReport extends Product with Serializable
  object ExecutionReport {
    final case class Stats(
      totalExecutions: Long,
      totalFails: Long,
      stepTimings: Map[String, Long],
      stepExecutions: Map[String, Long],
      stepFailings: Map[String, Long],
    ) extends ExecutionReport {
      override def toString: String = {
        s"""totalExecutions: $totalExecutions;
           |totalFails: $totalFails;
           |stepTimings: ${stepTimings.map { case (k, v) => s"$k=$v ms" }.mkString("[", ";", "]")};
           |stepExecutions: ${stepExecutions.map { case (k, v) => s"$k=$v" }.mkString("[", ";", "]")};
           |stepFailings: ${stepFailings.map { case (k, v) => s"$k=$v" }.mkString("[", ";", "]")};
           |""".stripMargin
      }
    }

    final case class ExecutionFailure(msg: String) extends ExecutionReport
  }
}
