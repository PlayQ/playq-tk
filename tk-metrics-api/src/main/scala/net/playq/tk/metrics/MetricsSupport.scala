package net.playq.tk.metrics

import org.http4s.HttpRoutes

trait MetricsSupport[F[_]] {
  def wrap(svc: HttpRoutes[F]): HttpRoutes[F]
}

object MetricsSupport {
  def empty[F[_]]: MetricsSupport[F] = identity
}
