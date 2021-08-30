package net.playq.tk.http

import org.http4s.HttpRoutes

trait TkHttp4sService[F[_, _], Ctx] {
  def acquireLog: F[Nothing, Unit]
  def prefix: String
  def httpRoutes: HttpRoutes[F[Throwable, _]]
}
