package net.playq.tk.loadtool

import izumi.functional.bio.{Async3, F}
import logstage.LogIO2.log
import logstage.LogIO3
import net.playq.tk.loadtool.scenario.Scenario.{ScenarioConfig, ScenarioWithConfig}
import net.playq.tk.loadtool.scenario.ScenarioContext.ExecutionReport.ExecutionFailure
import net.playq.tk.loadtool.scenario.ScenarioRunner.ScenarioWithReport
import net.playq.tk.loadtool.scenario.{Scenario, ScenarioRunner}

trait LoadTool[F[_, _]] {
  def run(config: ScenarioConfig): F[Nothing, ScenarioWithReport]
  def fetchAvailableScenarios: List[String]
}

object LoadTool {
  final case class LoadToolConfig(scenarios: Seq[ScenarioConfig])

  final class Impl[F[-_, +_, +_]: Async3: LogIO3](
    runner: ScenarioRunner[F],
    scenarios: Set[Scenario[F[Any, +_, +_], ?, ?]],
  ) extends LoadTool[F[Any, +_, +_]] {

    override def run(config: ScenarioConfig): F[Any, Nothing, ScenarioWithReport] = {
      scenarios.find(_.id == config.id) match {
        case Some(value) => executeScenario(ScenarioWithConfig(value, config))
        case None =>
          F.pure(ScenarioWithReport(config.id, ExecutionFailure(s"Impossible happened: worker knows nothing about ${config.id}")))
      }
    }

    override def fetchAvailableScenarios: List[String] = scenarios.map(_.id).toList

    @inline private[this] def executeScenario(scenario: ScenarioWithConfig[F[Any, +_, +_], ?, ?]): F[Any, Nothing, ScenarioWithReport] = {
      runner.run(scenario.scenario, scenario.config).catchAll {
        err =>
          log.error(s"Scenario ${scenario.scenario.id} execution failed due to $err.") *>
          F.pure(ScenarioWithReport(scenario.scenario.id, ExecutionFailure(err.toString)))
      }
    }
  }
}
