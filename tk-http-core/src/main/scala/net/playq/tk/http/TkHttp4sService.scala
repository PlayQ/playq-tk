package net.playq.tk.http

import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder2

/** A representation of an http4s route mounted by [[TkHttp4sServerResource]].
  *
  * Given a service `S[F] <:< TkHttp4sService[F]`, the expected bindings would be
  *
  * {{{
  * make[S[F]]
  * many[TkHttp4sService[F]].weak[S[F]]
  * }}}
  *
  * `S[F]` should be explicitly retained by a Role or another app entrypoint
  */
trait TkHttp4sService[F[_, _]] {
  def prefix: String

  def httpRoutes: WebSocketBuilder2[F[Throwable, _]] => HttpRoutes[F[Throwable, _]]

  /** Intended for debug logging purposes only, called once during HTTP server initialization.
    * Expected to log some useful info about the service being mounted.
    */
  def acquireLog: F[Nothing, Unit]
}
