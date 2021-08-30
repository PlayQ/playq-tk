package net.playq.tk.loadtool.scenario.gen

import java.util.concurrent.*

import cats.syntax.traverse.*
import izumi.distage.model.providers.Functoid
import izumi.distage.model.reflection.{DIKey, TypedRef}

import scala.jdk.CollectionConverters.*
import scala.util.Try

final class ScenarioGenProvider(
  defaultGens: Set[ScenarioGen[?]]
) {
  private[this] val instances = new ConcurrentHashMap[DIKey, ScenarioGen[?]](defaultGens.map(v => v.key -> v).toMap.asJava)

  def register(generators: ScenarioGen[?]*): Unit = generators.foreach(g => instances.put(g.key, g))

  def runWithGens[A](additionalGens: Map[DIKey, ScenarioGen[?]])(magnet: Functoid[A]): Either[Throwable, A] = {
    val all = instances.asScala ++ additionalGens
    for {
      params <- magnet.get.diKeys.toList.traverse {
        key =>
          all
            .get(key)
            .map(g => TypedRef(g.next, key.tpe, isByName = false))
            .toRight(new IllegalArgumentException(s"Cannot find an instance of $key in generators map."))
      }
      res <- Try(magnet.get.unsafeApply(params).asInstanceOf[A]).toEither
    } yield res
  }

  def run[A](magnet: Functoid[A]): Either[Throwable, A] = runWithGens(Map.empty)(magnet)
}

object ScenarioGenProvider {
  val empty: ScenarioGenProvider = new ScenarioGenProvider(Set.empty)
}
