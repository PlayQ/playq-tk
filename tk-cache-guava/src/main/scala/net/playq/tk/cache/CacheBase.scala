package net.playq.tk.cache

import com.google.common.cache.{Cache, CacheBuilder}
import izumi.functional.bio.{F, IO2}

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

@SuppressWarnings(Array("PointlessTypeBounds"))
object CacheBase {
  def apply[F[+_, +_]: IO2, E, K <: AnyRef, V <: AnyRef](cacheCfg: CacheConfig)(loadImpl: K => F[E, V]): CacheBase[F, E, K, V] = new CacheBase[F, E, K, V](cacheCfg) {
    override def load(key: K): F[E, V] = loadImpl(key)
  }
}

@SuppressWarnings(Array("PointlessTypeBounds"))
abstract class CacheBase[F[+_, +_]: IO2, E, K <: AnyRef, V <: AnyRef](
  cacheCfg: CacheConfig
) {

  protected def load(key: K): F[E, V]

  private[this] val cache: Cache[K, V] = {
    CacheBuilder
      .newBuilder()
      .maximumSize(cacheCfg.size)
      .expireAfterWrite(cacheCfg.ttl.toSeconds, TimeUnit.SECONDS)
      .softValues()
      .build[K, V]()
  }

  final def get(key: K): F[E, V] = {
    getIfPresent(key).flatMap {
      case Some(value) =>
        F.pure(value)
      case None =>
        reloadAndGet(key)
    }
  }

  final def getIfPresent(key: K): F[E, Option[V]] = {
    F.sync(Option(cache.getIfPresent(key)))
  }

  final def reloadAndGet(key: K): F[E, V] = {
    for {
      res <- load(key)
      _   <- F.sync(cache.put(key, res))
    } yield res
  }

  final def reloadAll(keys: Set[K]): F[E, Unit] = {
    F.traverse_(keys)(reloadAndGet(_).void)
  }

  final def invalidate(key: K): F[E, Unit] = {
    F.sync(cache.invalidate(key))
  }

  final def invalidateAll(): F[E, Unit] = {
    F.sync(cache.invalidateAll())
  }

  final def getAll: F[E, Map[K, V]] = {
    F.sync(cache.asMap().asScala.toMap)
  }
}
