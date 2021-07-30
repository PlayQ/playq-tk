package net.playq.tk.cache

import distage.Id
import izumi.functional.bio.{F, IO2}
import SimpleCacheWithAssertions.createValue

import scala.collection.mutable

class SimpleCacheWithAssertions[F[+_, +_]: IO2](cacheConfig: CacheConfig @Id("cache")) extends CacheBase[F, Throwable, String, String](cacheConfig) {
  def load(k: String): F[Throwable, String] = {
    F.sync(createValue(k))
  }
}

object SimpleCacheWithAssertions {
  val state = mutable.HashMap.empty[String, String]

  def genValue(k: String): String = {
    s"Value: $k"
  }

  def createValue(k: String): String = {
    assert(!state.contains(k))
    val v = genValue(k)
    state.put(k, v)
    v
  }
}
