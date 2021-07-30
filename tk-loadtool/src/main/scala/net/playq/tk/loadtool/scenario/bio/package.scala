package net.playq.tk.loadtool.scenario

import net.playq.tk.loadtool.scenario.bio.ScenarioIO2.ScenarioIO2SyntaxAux

package object bio {
  @inline final def SF[F[_, _], SF[+_, +_]](implicit FR: ScenarioIO2SyntaxAux[F, SF]): ScenarioIO2SyntaxAux[F, SF] = FR
}
