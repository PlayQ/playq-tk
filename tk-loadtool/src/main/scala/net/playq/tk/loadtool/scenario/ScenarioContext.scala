package net.playq.tk.loadtool.scenario

import java.time.ZonedDateTime
import izumi.functional.bio.{Clock2, Exit, F, IO2, Primitives2, Ref2}
import izumi.fundamentals.platform.strings.IzString.*
import logstage.LogIO2
import ScenarioContext.ExecutionReport
import ScenarioContext.ExecutionReport.Stats
import net.playq.tk.loadtool.scenario.gen.ScenarioGenProvider

trait ScenarioContext[F[_, _]] {
  val logger: LogIO2[F]
  val clock: Clock2[F]
  val scenarioGenProvider: ScenarioGenProvider

  def logReport: F[Nothing, Unit]
  def report: F[Nothing, ExecutionReport]

  private[loadtool] def wrap[E, A](label: String)(exec: F[E, A]): F[E, A]
  private[loadtool] def executed: F[Nothing, Long]

  private[loadtool] def fail: F[Nothing, Long]

  private[loadtool] def stepFailed(label: String): F[Nothing, Unit]
  private[loadtool] def stepSuccess(label: String, start: ZonedDateTime, end: ZonedDateTime): F[Nothing, Unit]
}

object ScenarioContext {

  def apply[F[+_, +_]: IO2: Primitives2](logger: LogIO2[F], clock: Clock2[F], scenarioGenProvider: ScenarioGenProvider): F[Nothing, ScenarioContext[F]] = {
    for {
      stepTimingsRef     <- F.mkRef(Map.empty[String, Double])
      stepExecutionsRef  <- F.mkRef(Map.empty[String, Long])
      stepFailingsRef    <- F.mkRef(Map.empty[String, Long])
      totalExecutionsRef <- F.mkRef(0L)
      totalFailsRef      <- F.mkRef(0L)
    } yield new Default(logger, clock, scenarioGenProvider, stepTimingsRef, stepExecutionsRef, stepFailingsRef, totalExecutionsRef, totalFailsRef)
  }

  final class Default[F[+_, +_]: IO2] private[ScenarioContext] (
    val logger: LogIO2[F],
    val clock: Clock2[F],
    val scenarioGenProvider: ScenarioGenProvider,
    // step metrics
    private[this] val stepTimingsRef: Ref2[F, Map[String, Double]],
    private[this] val stepExecutionsRef: Ref2[F, Map[String, Long]],
    private[this] val stepFailingsRef: Ref2[F, Map[String, Long]],
    // shared metrics
    private[this] val totalExecutionsRef: Ref2[F, Long],
    private[this] val totalFailsRef: Ref2[F, Long],
  ) extends ScenarioContext[F] {

    def logReport: F[Nothing, Unit] = {
      for {
        _ <- totalFailsRef.get.flatMap(totalFails => logger.info(s"Scenario $totalFails"))
        _ <- totalExecutionsRef.get.flatMap(totalExecutions => logger.info(s"Scenario $totalExecutions"))
        _ <- stepTimingsRef.get.flatMap(st => logger.info(s"Scenario ${st.map { case (k, v) => k -> "%.2f sec".format(v) }.niceList() -> "steps avg timings"}"))
        _ <- stepExecutionsRef.get.flatMap(se => logger.info(s"Scenario ${se.niceList() -> "steps executions number"}"))
        _ <- stepFailingsRef.get.flatMap(sf => logger.info(s"Scenario ${sf.niceList() -> "steps failings number"}"))
      } yield ()
    }

    def report: F[Nothing, ExecutionReport] = {
      for {
        totalFails <- totalFailsRef.get
        totalRuns  <- totalExecutionsRef.get

        timings    <- stepTimingsRef.get
        executions <- stepExecutionsRef.get
        fails      <- stepFailingsRef.get
      } yield Stats(totalRuns, totalFails, timings, executions, fails)
    }

    private[loadtool] def wrap[E, A](label: String)(exec: F[E, A]): F[E, A] = {
      for {
        _     <- logger.debug(s"Executing scenario step $label")
        start <- clock.now()
        ex    <- exec.sandboxExit
        end   <- clock.now()
        res <- ex match {
          case Exit.Success(v)             => stepSuccess(label, start, end) *> F.pure(v)
          case Exit.Error(err, _)          => stepFailed(label) *> F.fail(err)
          case Exit.Termination(err, _, _) => stepFailed(label) *> F.terminate(err)
          case Exit.Interruption(err, _)   => stepFailed(label) *> F.terminate(err)
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
      val cur = end.toEpochSecond - start.toEpochSecond
      for {
        _ <- stepTimingsRef.update(_.updatedWith(label)(_.map(prev => (prev + cur.toDouble) / 2d).orElse(Some(cur.toDouble))))
        _ <- stepExecutionsRef.update(_.updatedWith(label)(_.map(_ + 1L).orElse(Some(1L))))
      } yield ()
    }
  }

  sealed trait ExecutionReport extends Product with Serializable
  object ExecutionReport {
    final case class Stats(
      totalExecutions: Long,
      totalFails: Long,
      stepTimings: Map[String, Double],
      stepExecutions: Map[String, Long],
      stepFailings: Map[String, Long],
    ) extends ExecutionReport

    final case class ExecutionFailure(msg: String) extends ExecutionReport
  }
}
