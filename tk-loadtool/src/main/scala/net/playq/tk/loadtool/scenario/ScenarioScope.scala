package net.playq.tk.loadtool.scenario

import izumi.distage.model.reflection.DIKey

final case class ScenarioScope(
  additionalGens: Map[DIKey, ScenarioGen[?]]
) {
  def add(generators: Set[ScenarioGen[?]]): ScenarioScope = {
    this.copy(additionalGens = additionalGens ++ generators.map(v => v.key -> v))
  }
  def ++(that: ScenarioScope): ScenarioScope = {
    ScenarioScope(this.additionalGens ++ that.additionalGens)
  }
}

object ScenarioScope {
  def apply(generators: Set[ScenarioGen[?]]): ScenarioScope = ScenarioScope(generators.map(v => v.key -> v).toMap)
  val empty: ScenarioScope                                  = new ScenarioScope(Map.empty)
}
