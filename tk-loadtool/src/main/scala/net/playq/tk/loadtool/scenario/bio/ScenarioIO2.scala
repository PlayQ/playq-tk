package net.playq.tk.loadtool.scenario.bio

import distage.{Functoid, Tag}
import izumi.distage.model.effect.QuasiApplicative
import izumi.functional.bio.syntax.Syntax2.PanicOps
import izumi.functional.bio.{F, IO3, Local3, Panic2, Ref3}
import net.playq.tk.loadtool.scenario.{ScenarioContext, ScenarioGen, ScenarioScope}
import zio.Has

import scala.language.implicitConversions

object ScenarioIO2 {
  private type Ref[F[-_, +_, +_]] = Ref3[F, ScenarioScope]
  private type Ctx[F[-_, +_, +_]] = ScenarioContext[F[Any, _, _]]

  private sealed trait ScopeAccess[+F[-_, +_, +_], +E, +A]
  private final case class ScopeEffect[F[-_, +_, +_], E, A](fun: Functoid[F[Any, E, A]]) extends ScopeAccess[F, E, A]
  private final case class ScopeIdentity[A](fun: Functoid[A]) extends ScopeAccess[Nothing, Nothing, A]

  trait ScenarioIO2Syntax[F[_, _]] {
    final type l[SF[+_, +_]] = ScenarioIO2SyntaxAux[F, SF]

    type ScenarioF[+_, +_]

    def Panic2: Panic2[ScenarioF]

    /** Bind new [[ScenarioGen]] into current interpreter scope. For the end of the scenario block or till [[newScope]] is called. */
    def bind(generators: ScenarioGen[?]*): ScenarioF[Nothing, Unit]

    /** Create empty scope */
    def newScope(generators: ScenarioGen[?]*): ScenarioF[Nothing, Unit]

    /** Create scenario step from pure value. */
    def pure[A](a: A): ScenarioF[Nothing, A]

    /** Create scenario step and wrap value into sync. */
    def sync[A](label: String)(exec: () => A): ScenarioF[Nothing, A]

    /** Create scenario step from effect. */
    def fromF[E, A](label: String)(exec: F[E, A]): ScenarioF[E, A]

    /** Create scenario step from effect. */
    def unrecorded[E, A](exec: F[E, A]): ScenarioF[E, A]

    /** Execute step with magnet of values from default and scoped generators. */
    def step[A](label: String)(fun: Functoid[A]): ScenarioF[Nothing, A]

    /** Execute step with magnet of values from default and scoped generators. */
    def stepF[E, A](label: String)(fun: Functoid[F[E, A]]): ScenarioF[E, A]

    def unrecordedStep[E, A](label: String)(fun: Functoid[A]): ScenarioF[Nothing, A]

    def unrecordedStepF[E, A](label: String)(fun: Functoid[F[E, A]]): ScenarioF[E, A]

    def bracket[E, A1, B](
      acquire: ScenarioF[E, A1]
    )(release: A1 => ScenarioF[Nothing, Unit]
    )(use: A1 => ScenarioF[E, B]
    ): ScenarioF[E, B]

    def traverse[A, E, B](l: Iterable[A])(f: A => ScenarioF[E, B]): ScenarioF[E, List[B]]

    def unit: ScenarioF[Nothing, Unit]
  }
  trait ScenarioIO2SyntaxAux[F[_, _], SF[+_, +_]] extends ScenarioIO2Syntax[F] {
    type ScenarioF[+E, +A] = SF[E, A]
  }

  implicit def ScenarioIO2Syntax[F[_, _], SF[+_, +_], E, A](f: SF[E, A])(implicit SF: ScenarioIO2SyntaxAux[F, SF]): PanicOps[SF, E, A] =
    new PanicOps[SF, E, A](f)(SF.Panic2)
  implicit def ScenarioIO2Syntax[F[_, _], SF[+_, +_], E](implicit SF: ScenarioIO2SyntaxAux[F, SF]): QuasiApplicative[SF[E, _]] =
    QuasiApplicative.fromBIO(SF.Panic2)

  final class Impl[F[-_, +_, +_]: IO3: Local3](
    implicit
    t1: Tag[Ref3[F, ScenarioScope]],
    t2: Tag[ScenarioContext[F[Any, _, _]]],
  ) extends ScenarioIO2SyntaxAux[F[Any, +_, +_], F[Has[Ref3[F, ScenarioScope]] with Has[ScenarioContext[F[Any, _, _]]], +_, +_]] {
    override val Panic2: Panic2[ScenarioF] = implicitly

    override def bind(generators: ScenarioGen[?]*): ScenarioF[Nothing, Unit] = {
      modifyScope(_.add(generators.toSet))
    }
    override def newScope(generators: ScenarioGen[?]*): ScenarioF[Nothing, Unit] = {
      modifyScope(_ => ScenarioScope(generators.toSet))
    }
    override def pure[A](a: A): ScenarioF[Nothing, A] = {
      F.pure(a)
    }
    override def sync[A](label: String)(exec: () => A): ScenarioF[Nothing, A] = {
      record(label)(_ => F.sync(exec()))
    }
    override def fromF[E, A](label: String)(exec: F[Any, E, A]): ScenarioF[E, A] = {
      record(label)(_ => exec)
    }
    override def unrecorded[E, A](exec: F[Any, E, A]): ScenarioF[E, A] = {
      exec
    }
    override def stepF[E, A](label: String)(fun: Functoid[F[Any, E, A]]): ScenarioF[E, A] = {
      accessScope(label)(ScopeEffect(fun))
    }
    override def step[A](label: String)(fun: Functoid[A]): ScenarioF[Nothing, A] = {
      accessScope(label)(ScopeIdentity(fun))
    }

    override def unrecordedStepF[E, A](label: String)(fun: Functoid[F[Any, E, A]]): ScenarioF[E, A] = {
      accessScope(label, isUnrecorded = true)(ScopeEffect(fun))
    }

    override def unrecordedStep[E, A](label: String)(fun: Functoid[A]): ScenarioF[Nothing, A] = {
      accessScope(label, isUnrecorded = true)(ScopeIdentity(fun))
    }

    override def bracket[E, A1, B](
      acquire: ScenarioF[E, A1]
    )(release: A1 => ScenarioF[Nothing, Unit]
    )(use: A1 => ScenarioF[E, B]
    ): ScenarioF[E, B] = {
      acquire.bracket(release)(use)
    }

    override def traverse[A, E, B](l: Iterable[A])(f: A => ScenarioF[E, B]): ScenarioF[E, List[B]] = {
      F.traverse(l)(f(_))
    }

    override val unit: ScenarioF[Nothing, Unit] = F.unit

    private[this] def record[E, A](label: String)(exec: ScenarioScope => F[Any, E, A]): ScenarioF[E, A] = {
      F.access(_.get[Ctx[F]].wrap(label)(exec(ScenarioScope.empty)))
    }

    private[this] def modifyScope(scenarioUpdate: ScenarioScope => ScenarioScope): ScenarioF[Nothing, Unit] = {
      F.access(_.get[Ref[F]].update_(scenarioUpdate))
    }

    private[this] def accessScope[E, A](label: String, isUnrecorded: Boolean = false)(access: ScopeAccess[F, E, A]): ScenarioF[E, A] = {
      F.access {
        has =>
          val ctx      = has.get[Ctx[F]]
          val scopeRef = has.get[Ref[F]]
          val result = for {
            scope <- scopeRef.get
            res <- (access match {
              case ScopeEffect(fun) =>
                F.fromEither(ctx.scenarioGenProvider.runWithGens(scope.additionalGens)(fun)).orTerminate.flatten
              case ScopeIdentity(fun) =>
                F.fromEither(ctx.scenarioGenProvider.runWithGens(scope.additionalGens)(fun)).orTerminate
            }): F[Any, E, A]
          } yield res
          F.ifThenElse(isUnrecorded)(result, ctx.wrap(label)(result: F[Any, E, A]))
      }
    }
  }
}
