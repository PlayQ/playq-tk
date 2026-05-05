package net.playq.tk.metrics

import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder2

trait MetricsSupport[F[_]] {
  def wrap(svc: WebSocketBuilder2[F] => HttpRoutes[F]): WebSocketBuilder2[F] => HttpRoutes[F]
}

object MetricsSupport {
  def empty[F[_]]: MetricsSupport[F] = identity
}
