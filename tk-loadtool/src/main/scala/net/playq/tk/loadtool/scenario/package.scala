package net.playq.tk.loadtool

import izumi.functional.bio.ApplicativeError2
import izumi.functional.bio.data.Morphism1
import izumi.functional.lifecycle.Lifecycle

package object scenario {
  type SF[F[+_, +_], +E, +A] = ScenarioF[F, E, A]
  val SF: ScenarioF.type = scenario.ScenarioF

  type ScenarioAny[F[+_, +_]] = Scenario[F, ?, ?]

  type ScenarioBuilder[F[+_, +_], +E, +A] = Lifecycle[SF[F, E, _], SF[F, E, A]]
  object ScenarioBuilder {
    def fromLifecycle[F[+_, +_], E, A](l: Lifecycle[F[E, _], A]): Lifecycle[SF[F, E, _], A] = {
      l.mapK[F[E, _], SF[F, E, _]](Morphism1(f => SF.liftUnrecorded(f)))
    }
    def fromLifecycleLeftMap[F[+_, +_]: ApplicativeError2, E, A, E1](conv: E => E1)(l: Lifecycle[F[E, _], A]): Lifecycle[SF[F, E1, _], A] = {
      l.mapK[F[E, _], SF[F, E1, _]](Morphism1(f => SF.liftUnrecorded(f.leftMap(conv))))
    }
  }
}
