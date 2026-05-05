package net.playq.tk.cache

import com.google.common.cache.*
import izumi.functional.bio.{F, IO2}
import net.playq.tk.cache.CacheBase.CacheRemovalListener

import java.util.concurrent.Executor
import scala.jdk.CollectionConverters.*
import scala.jdk.DurationConverters.ScalaDurationOps
import scala.util.chaining.scalaUtilChainingOps

@SuppressWarnings(Array("PointlessTypeBounds"))
object CacheBase {
  def apply[F[+_, +_]: IO2, E, K <: AnyRef, V <: AnyRef](cacheCfg: CacheConfig)(loadImpl: K => F[E, V]): CacheBase[F, E, K, V] = new CacheBase[F, E, K, V](cacheCfg) {
    override def load(key: K): F[E, V] = loadImpl(key)
  }

  trait CacheRemovalListener[K, V] {
    def toRemovalListener: RemovalListener[K, V]
  }
  object CacheRemovalListener {
    final case class Sync[K, V](listener: (K, V) => Unit) extends CacheRemovalListener[K, V] {
      override def toRemovalListener: RemovalListener[K, V] = new RemovalListener[K, V] {
        override def onRemoval(notification: RemovalNotification[K, V]): Unit = {
          listener(notification.getKey, notification.getValue)
        }
      }
    }
    final case class Async[K, V](executor: Executor)(l: (K, V) => Unit) extends CacheRemovalListener[K, V] {
      override def toRemovalListener: RemovalListener[K, V] = {
        RemovalListeners.asynchronous(Sync(l).toRemovalListener, executor)
      }
    }
  }
}

@SuppressWarnings(Array("PointlessTypeBounds"))
abstract class CacheBase[F[+_, +_]: IO2, E, K <: AnyRef, V <: AnyRef](
  cacheCfg: CacheConfig
) {

  protected def load(key: K): F[E, V]

  protected def removalListener: Option[CacheRemovalListener[K, V]] = None
  protected def useSoftReferences: Boolean                          = true

  private[this] val cache: Cache[K, V] = {
    val baseBuilder = CacheBuilder
      .newBuilder()
      .maximumSize(cacheCfg.size)
      .expireAfterWrite(cacheCfg.ttl.toJava)
      .pipe(b => if (useSoftReferences) b.softValues() else b)
    removalListener.fold(baseBuilder.build[K, V]()) {
      crl =>
        baseBuilder
          .removalListener[K, V](crl.toRemovalListener)
          .build[K, V]()
    }
  }

  /** NOTE: `get`/`reload` are not synchronized between callers, multiple threads may try to reload the cache value at the same time */
  final def get(key: K): F[E, V] = {
    getIfPresent(key).flatMap {
      case Some(value) =>
        F.pure(value)
      case None =>
        reloadAndGet(key)
    }
  }

  final def getIfPresent(key: K): F[Nothing, Option[V]] = {
    F.sync(Option(cache.getIfPresent(key)))
  }

  final def reloadAndGet(key: K): F[E, V] = {
    for {
      res <- load(key)
      _   <- put(key, res)
    } yield res
  }

  final def reloadAll(keys: Set[K]): F[E, Unit] = {
    F.traverse_(keys)(reloadAndGet(_).void)
  }

  final def invalidate(key: K): F[Nothing, Unit] = {
    F.sync(cache.invalidate(key))
  }

  final def invalidateAll(): F[Nothing, Unit] = {
    F.sync(cache.invalidateAll())
  }

  final def getAll: F[Nothing, Map[K, V]] = {
    F.sync(cache.asMap().asScala.toMap)
  }

  final def put(key: K, value: V): F[Nothing, Unit] = {
    F.sync(cache.put(key, value))
  }

  final def putAll(m: Map[K, V]): F[Nothing, Unit] = {
    F.sync(cache.putAll(m.asJava))
  }
}
