package net.playq.tk.loadtool.scenario

import distage.{ClassConstructor, Functoid, Tag}
import izumi.functional.bio.data.{Morphism1, RestoreInterruption1}
import izumi.functional.bio.{Applicative2, Bracket2, Error2, Exit, F, IO2, Monad2, Panic2, TypedError}
import izumi.functional.quasi.{QuasiPrimitives, QuasiRef}
import net.playq.tk.loadtool.scenario.ScenarioF.ScenarioExecutionContext

final class ScenarioF[F[+_, +_], +E, +A] private (val eff: ScenarioExecutionContext[F] => F[E, A]) extends AnyVal {
  def flatMap[E1 >: E, B](f: A => ScenarioF[F, E1, B])(implicit M: Monad2[F]): ScenarioF[F, E1, B] = ScenarioF.apply {
    ctx =>
      eff(ctx).flatMap(f(_).eff(ctx))
  }
  def *>[E1 >: E, B](f: => ScenarioF[F, E1, B])(implicit M: Monad2[F]): ScenarioF[F, E1, B]                                    = flatMap(_ => f)
  def <*[E1 >: E, B](f: => ScenarioF[F, E1, B])(implicit M: Monad2[F]): ScenarioF[F, E1, A]                                    = flatMap(r => f.as(r))
  def map[B](f: A => B)(implicit M: Monad2[F]): ScenarioF[F, E, B]                                                             = ScenarioF.apply(eff(_).map(f))
  def leftMap[E1](f: E => E1)(implicit M: Error2[F]): ScenarioF[F, E1, A]                                                      = ScenarioF.apply(eff(_).leftMap(f))
  def as[B](b: B)(implicit M: Monad2[F]): ScenarioF[F, E, B]                                                                   = map(_ => b)
  def sandbox(implicit P: Panic2[F]): ScenarioF[F, Exit.Failure[E], A]                                                         = ScenarioF.apply(eff(_).sandbox)
  def sandboxExit(implicit P: Panic2[F]): ScenarioF[F, Nothing, Exit[E, A]]                                                    = ScenarioF.apply(eff(_).sandboxExit)
  def redeem[E1, B](err: E => ScenarioF[F, E1, B], succ: A => ScenarioF[F, E1, B])(implicit E: Error2[F]): ScenarioF[F, E1, B] = ScenarioF.apply {
    ctx =>
      eff(ctx).redeem(
        e => err(e).eff(ctx),
        a => succ(a).eff(ctx),
      )
  }
  def orTerminate(implicit M: Panic2[F], ev: E <:< Throwable): ScenarioF[F, Nothing, A]                  = ScenarioF.apply(eff(_).catchAll(F.terminate(_)))
  def catchAll[E1, A1 >: A](err: E => ScenarioF[F, E1, A1])(implicit E: Error2[F]): ScenarioF[F, E1, A1] = redeem[E1, A1](err, SF.pure(_))
  def bracket[E1 >: E, B](release: A => ScenarioF[F, Nothing, Unit])(use: A => ScenarioF[F, E1, B])(implicit B: Bracket2[F]): ScenarioF[F, E1, B] = ScenarioF.apply {
    ctx =>
      eff(ctx).bracket[E1, B](release(_).eff(ctx))(use(_).eff(ctx))
  }
  def bracketCase[E1 >: E, B](release: (A, Exit[E1, B]) => ScenarioF[F, Nothing, Unit])(use: A => ScenarioF[F, E1, B])(implicit B: Bracket2[F]): ScenarioF[F, E1, B] =
    ScenarioF.apply {
      ctx =>
        eff(ctx).bracketCase[E1, B] {
          case (a, e) => release(a, e).eff(ctx)
        }(use(_).eff(ctx))
    }
  def void(implicit M: Monad2[F]): ScenarioF[F, E, Unit] = as(())
}

object ScenarioF {

  private def apply[F[+_, +_], E, A](eff: ScenarioExecutionContext[F] => F[E, A]) = new SF[F, E, A](eff)

  implicit def ScenarioFQuasiPrimitives[F[+_, +_], E](implicit F: IO2[F]): QuasiPrimitives[ScenarioF[F, E, _]] = {
    new QuasiPrimitives[ScenarioF[F, E, _]] {

      override def flatMap[A, B](fa: SF[F, E, A])(f: A => SF[F, E, B]): SF[F, E, B] = {
        fa.flatMap(f)
      }
      override def bracket[A, B](acquire: => SF[F, E, A])(release: A => SF[F, E, Unit])(use: A => SF[F, E, B]): SF[F, E, B] = {
        SF.bracket(acquire)(
          release(_).catchAll {
            case e: Throwable => ScenarioF(_ => F.terminate(e))
            case e            => ScenarioF(_ => F.terminate(TypedError.noMessage(e)))
          }
        )(use)
      }

      override def tapBothUntyped[A](eff: => SF[F, E, A])(err: Any => SF[F, E, Unit], succ: A => SF[F, E, Unit]): SF[F, E, A] = {
        eff.redeem(e => err(e).flatMap(_ => SF.fail(e)), a => succ(a).as(a))
      }

      override def uninterruptibleExcept[A](f: RestoreInterruption1[SF[F, E, _]] => SF[F, E, A]): SF[F, E, A] = {
        ScenarioF(ctx => F.uninterruptibleExcept(restoreF => f(Morphism1[SF[F, E, _], SF[F, E, _]](sf => SF.liftUnrecorded(restoreF(sf.eff(ctx))))).eff(ctx)))
      }

      override def mkRef[A](a: A): SF[F, E, QuasiRef[SF[F, E, _], A]] = {
        QuasiRef.fromMaybeSuspend(a)(Morphism1(f => ScenarioF(_ => F.sync(f()))))
      }

      override def suspendF[A](effAction: => SF[F, E, A]): SF[F, E, A] = {
        ScenarioF((_: ScenarioExecutionContext[F]) => F.sync(effAction)).flatMap(identity)
      }

      override def pure[A](a: A): ScenarioF[F, E, A] = {
        ScenarioF.pure(a)
      }
      override def map2[A, B, C](fa: ScenarioF[F, E, A], fb: => ScenarioF[F, E, B])(f: (A, B) => C): ScenarioF[F, E, C] = {
        fa.flatMap(a => fb.map(b => f(a, b)))
      }
      override def map[A, B](fa: ScenarioF[F, E, A])(f: A => B): ScenarioF[F, E, B] = {
        fa.map(f)
      }
      override def traverse[A, B](l: Iterable[A])(f: A => ScenarioF[F, E, B]): ScenarioF[F, E, List[B]] = {
        ScenarioF.traverse(l)(f)
      }
      override def traverse_[A](l: Iterable[A])(f: A => ScenarioF[F, E, Unit]): ScenarioF[F, E, Unit] = {
        ScenarioF.traverse_(l)(f)
      }
    }
  }

  final case class ScenarioExecutionContext[F[_, _]](context: ScenarioContext[F], gens: ScenarioGens)

  /** Bind new [[ScenarioGen]] into current interpreter scope. For the end of the scenario block or till [[genReset]] is called. */
  def genBindMany[F[+_, +_]: IO2](generators: ScenarioGen[?]*): ScenarioF[F, Nothing, Unit] = ScenarioF.apply {
    ctx => F.traverse_(generators)(g => F.fromEither(ctx.gens.bind(g))).orTerminate
  }

  def gen[F[+_, +_]: IO2, T: Tag]: ScenarioF[F, Nothing, T] = ScenarioF.apply {
    ctx => F.fromEither(ctx.gens.gen[T]).orTerminate
  }

  def genBindFrom[F[+_, +_]: IO2, T: Tag](fun: Functoid[T]): ScenarioF[F, Nothing, Unit] = ScenarioF.apply {
    ctx => F.fromEither(ctx.gens.bind(ScenarioGen.from(fun))).orTerminate
  }

  def genBindOf[F[+_, +_]: IO2, T: Tag: ClassConstructor]: ScenarioF[F, Nothing, Unit] = ScenarioF.apply {
    ctx => F.fromEither(ctx.gens.bind(ScenarioGen.of[T])).orTerminate
  }

  def genBindConst[F[+_, +_]: IO2, T: Tag](v: T): ScenarioF[F, Nothing, Unit] = ScenarioF.apply {
    ctx => F.fromEither(ctx.gens.bind(ScenarioGen.const(v))).orTerminate
  }

  /** Create empty scope */
  def genReset[F[+_, +_]: Applicative2](generators: ScenarioGen.Gen[?]*): ScenarioF[F, Nothing, Unit] = ScenarioF.apply {
    ctx => F.pure(ctx.gens.reset(generators.toSet)).void
  }

  /** Create scenario step from pure value. */
  def pure[F[+_, +_]: Applicative2, A](a: A): ScenarioF[F, Nothing, A] = ScenarioF.apply {
    _ => F.pure(a)
  }

  /** Create scenario step and wrap value into sync. */
  def sync[F[+_, +_]: IO2, A](label: String)(exec: => A): ScenarioF[F, Nothing, A] = ScenarioF.apply {
    _.context.wrap(label)(F.sync(exec))
  }

  /** Create scenario step and wrap value into sync without its recording. */
  def syncUnrecorded[F[+_, +_]: IO2, A](exec: => A): ScenarioF[F, Nothing, A] = ScenarioF.apply {
    _ => F.sync(exec)
  }

  /** Create scenario step from effect. */
  def lift[F[+_, +_], E, A](label: String)(exec: F[E, A]): ScenarioF[F, E, A] = ScenarioF.apply[F, E, A] {
    _.context.wrap(label)(exec)
  }

  /** Create scenario step from effect without its recording. */
  def liftUnrecorded[F[+_, +_], E, A](exec: F[E, A]): ScenarioF[F, E, A] = ScenarioF.apply {
    _ => exec
  }

  /** Execute step with magnet of values from default and scoped generators. */
  def syncWith[F[+_, +_]: IO2, A](label: String)(fun: Functoid[A]): ScenarioF[F, Nothing, A] = {
    accessScope(Some(label))(Left(fun))
  }

  /** Execute step with magnet of values from default and scoped generators without its recording. */
  def syncUnrecordedWith[F[+_, +_]: IO2, E, A](fun: Functoid[A]): ScenarioF[F, Nothing, A] = {
    accessScope(None)(Left(fun))
  }

  /** Execute step with magnet of values from default and scoped generators. */
  def liftWith[F[+_, +_]: IO2, E, A](label: String)(fun: Functoid[F[E, A]]): ScenarioF[F, E, A] = {
    accessScope(Some(label))(Right(fun))
  }

  /** Execute step with magnet of values from default and scoped generators without its recording. */
  def liftUnrecordedWith[F[+_, +_]: IO2, E, A](fun: Functoid[F[E, A]]): ScenarioF[F, E, A] = {
    accessScope(None)(Right(fun))
  }

  def bracket[F[+_, +_]: Bracket2, E, A1, B](
    acquire: ScenarioF[F, E, A1]
  )(release: A1 => ScenarioF[F, Nothing, Unit]
  )(use: A1 => ScenarioF[F, E, B]
  ): ScenarioF[F, E, B] = {
    acquire.bracket(release)(use)
  }

  def traverse[F[+_, +_]: Monad2, A, E, B](l: Iterable[A])(f: A => ScenarioF[F, E, B]): ScenarioF[F, E, List[B]] = {
    l.foldLeft[ScenarioF[F, E, List[B]]](pure(Nil)) {
      case (acc, s) => f(s).flatMap(ell => acc.map(_.appended(ell)))
    }
  }

  def traverse_[F[+_, +_]: Monad2, A, E, B](l: Iterable[A])(f: A => ScenarioF[F, E, B]): ScenarioF[F, E, Unit] = {
    l.foldLeft[ScenarioF[F, E, Unit]](unit) {
      case (acc, s) => f(s) *> acc
    }
  }

  def fail[F[+_, +_]: Error2, E](err: E): ScenarioF[F, E, Nothing] = liftUnrecorded(F.fail(err))

  def unit[F[+_, +_]: Monad2]: ScenarioF[F, Nothing, Unit] = liftUnrecorded(F.unit)

  private def accessScope[F[+_, +_]: IO2, E, A](label: Option[String])(access: Either[Functoid[A], Functoid[F[E, A]]]): ScenarioF[F, E, A] = ScenarioF.apply {
    case ScenarioExecutionContext(ctx, gens) =>
      val wrap: F[E, A] => F[E, A] = label match {
        case Some(value) => ctx.wrap(value)
        case None        => identity
      }
      val result = access match {
        case Right(fun) => F.fromEither(gens.run(fun)).orTerminate.flatten
        case Left(fun)  => F.fromEither(gens.run(fun)).orTerminate
      }
      wrap(result)
  }
}
