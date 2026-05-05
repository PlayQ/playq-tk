package net.playq.tk.loadtool

import izumi.functional.bio.{Async2, F}
import net.playq.tk.loadtool.scenario.Scenario.{ScenarioConfig, ScenarioWithConfig}
import net.playq.tk.loadtool.scenario.ScenarioContext.ExecutionReport.ExecutionFailure
import net.playq.tk.loadtool.scenario.ScenarioRunner.ScenarioWithReport
import net.playq.tk.loadtool.scenario.{ScenarioAny, ScenarioRunner}

trait LoadTool[F[_, _]] {
  def run(config: ScenarioConfig): F[Nothing, ScenarioWithReport]
  def fetchAvailableScenarios: List[String]
}

object LoadTool {
  final case class LoadToolConfig(scenarios: Seq[ScenarioConfig])

  final class Impl[F[+_, +_]: Async2](
    runner: ScenarioRunner[F],
    scenarios: Set[ScenarioAny[F[+_, +_]]],
  ) extends LoadTool[F[+_, +_]] {

    override def run(config: ScenarioConfig): F[Nothing, ScenarioWithReport] = {
      // FIXME: this assert is temporal, we need it until we are not fixed an issue with an empty scenarios set.
      assert(scenarios.nonEmpty)

      scenarios.find(_.id == config.id) match {
        case Some(value) => executeScenario(ScenarioWithConfig(value, config))
        case None        =>
          F.pure(ScenarioWithReport(config.id, ExecutionFailure(s"Impossible happened: worker knows nothing about ${config.id}")))
      }
    }

    override def fetchAvailableScenarios: List[String] = scenarios.map(_.id).toList

    @inline private[this] def executeScenario(scenario: ScenarioWithConfig[F, ?, ?]): F[Nothing, ScenarioWithReport] = {
      runner.run(scenario.scenario, scenario.config)
    }
  }
}
