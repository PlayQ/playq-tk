package net.playq.tk.loadtool.scenario

import izumi.distage.config.codec.PureconfigAutoDerive
import izumi.distage.model.definition.Lifecycle
import net.playq.tk.loadtool.scenario.bio.ScenarioIO2.ScenarioIO2Syntax
import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

trait Scenario[F[+_, +_], E, A] {
  val id: String
  def mkScenario[F1[+_, +_]: ScenarioIO2Syntax[F]#l]: Lifecycle[F1[E, ?], F1[E, A]]
}

object Scenario {
  final case class ScenarioConfig(id: String, onUsers: Int, pauseForUser: FiniteDuration, userScenarioRepeat: Int)
  object ScenarioConfig extends {
    final case class ConfigStored(scenarios: Seq[ScenarioConfig])
    object ConfigStored {
      implicit val reader: ConfigReader[ConfigStored] = PureconfigAutoDerive[ConfigStored]
    }
  }
  final case class ScenarioWithConfig[F[+_, +_], E, A](scenario: Scenario[F, E, A], config: ScenarioConfig)
}
