package net.playq.tk.npmresolver.caches

import izumi.functional.bio.Root.AttachPrimitives
import izumi.functional.bio.{F, IO2, Primitives2, Promise2}

import java.util.concurrent.ConcurrentHashMap

/** This is just a local per-invokation cache for graph traversals to avoid visiting the same node multiple times
  */
class ConcurrentActionCache[F[+_, +_]: IO2: Primitives2, K, V] {
  private val content = new ConcurrentHashMap[K, Promise2[F, Throwable, V]]()

  def retrieve(key: K, compute: K => F[Throwable, V]): F[Throwable, V] = {
    for {
      promise    <- F.mkPromise[Throwable, V]
      maybeState <- F.sync(
        content.computeIfAbsent(key, _ => promise)
      )
      out <- maybeState match {
        case expromise if expromise eq promise =>
          for {
            value <- compute(key)
            _     <- promise.succeed(value)
          } yield {
            value
          }
        case expromise =>
          for {
            value <- expromise.await
          } yield {
            value
          }
      }
    } yield {
      out
    }
  }
}
