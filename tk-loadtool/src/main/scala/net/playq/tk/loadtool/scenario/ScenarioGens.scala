package net.playq.tk.loadtool.scenario

import cats.implicits.toTraverseOps
import distage.{ClassConstructor, Tag}
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{DIKey, TypedRef}
import izumi.functional.bio.{F, IO2}
import net.playq.tk.loadtool.scenario.ScenarioGens.MissingGenException

import scala.collection.mutable
import scala.util.Try

final case class ScenarioGens(
  initial: Map[DIKey, ScenarioGen.Gen[?]]
) {
  private val gens: mutable.Map[DIKey, ScenarioGen.Gen[?]] = collection.mutable.HashMap.from(initial)

  def rechargeGenerators[F[+_, +_]: IO2](size: Int): F[Nothing, Unit] = {
    F.traverse_(gens) {
      case (_, gen) =>
        F.sync(gen.recharge(size)).sandboxExit.void
    }
  }

  def reset(generators: Set[ScenarioGen.Gen[?]]): Unit = {
    synchronized {
      gens.clear()
      gens.addAll(initial ++ generators.map(g => g.key -> g))
      ()
    }
  }

  def bind[T](scenarioGen: ScenarioGen.FromFunctoid[T]): Either[Throwable, Unit] = {
    synchronized {
      for {
        paramGens <- scenarioGen.fun.get.diKeys.toList.traverse(getGenerator)
        gen        = ScenarioGen.apply[T] {
          val params = paramGens.map(_.nextValueTyped)
          scenarioGen.fun.get.unsafeApply(params).asInstanceOf[T]
        }(using scenarioGen.tag)
        _ = gens.put(scenarioGen.key, gen)
      } yield ()
    }
  }

  def bind[T](scenarioGen: ScenarioGen.Gen[T]): Unit = {
    synchronized {
      gens.put(scenarioGen.key, scenarioGen)
      ()
    }
  }

  def bind[T](scenarioGen: ScenarioGen[T]): Either[Throwable, Unit] = {
    scenarioGen match {
      case simple: ScenarioGen.Gen[T]                   => Right(bind(simple))
      case from: ScenarioGen.FromFunctoid[T] @unchecked => bind(from)
    }
  }

  def gen[T: Tag]: Either[Throwable, T] = {
    genValue[T]
  }

  def genOf[T: Tag: ClassConstructor]: Either[Throwable, T] = {
    genValue[T] match {
      case Left(_: MissingGenException) =>
        bind(ScenarioGen.of[T]).flatMap(_ => genValue[T])
      case other =>
        other
    }
  }

  def genFrom[T: Tag](fun: Functoid[T]): Either[Throwable, T] = {
    genValue[T] match {
      case Left(_: MissingGenException) =>
        bind(ScenarioGen.from(fun)).flatMap(_ => genValue[T])
      case other =>
        other
    }
  }

  def run[A](magnet: Functoid[A]): Either[Throwable, A] = {
    for {
      params <- magnet.get.diKeys.toList.traverse(genValueTyped)
      res    <- Try(magnet.get.unsafeApply(params).asInstanceOf[A]).toEither
    } yield res
  }

  private[this] def getGenerator(key: DIKey): Either[Throwable, ScenarioGen.Gen[?]] = {
    gens.get(key).toRight(MissingGenException(key))
  }

  private[this] def genValueTyped(key: DIKey): Either[Throwable, TypedRef[?]] = {
    getGenerator(key).flatMap(g => Try(g.nextValueTyped).toEither)
  }

  private[this] def genValue[T: Tag]: Either[Throwable, T] = {
    getGenerator(DIKey[T]).flatMap(g => Try(g.nextValue.asInstanceOf[T]).toEither)
  }
}

object ScenarioGens {
  final case class MissingGenException(key: DIKey) extends RuntimeException(s"Cannot find an instance of $key in generators map.")
  def apply(generators: Set[ScenarioGen.Gen[?]]): ScenarioGens = new ScenarioGens(generators.map(v => v.key -> v).toMap)
  val empty: ScenarioGens                                      = new ScenarioGens(Map.empty)
}
