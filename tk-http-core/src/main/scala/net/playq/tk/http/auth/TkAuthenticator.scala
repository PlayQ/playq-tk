package net.playq.tk.http.auth

import cats.data.{Kleisli, OptionT}
import izumi.functional.bio.{F, IO2}
import org.http4s._
import org.http4s.server.AuthMiddleware
import net.playq.tk.quantified.SyncThrowable

abstract class TkAuthenticator[F[+_, +_]: IO2: SyncThrowable, BasicContext] {
  /** should be similar to [[AuthMiddleware.apply]] **/
  final val authenticator: AuthMiddleware[F[Throwable, ?], BasicContext] = {
    (routes: AuthedRoutes[BasicContext, F[Throwable, ?]]) =>
      Kleisli {
        (req: Request[F[Throwable, ?]]) =>
          OptionT.liftF {
            authenticate(req)
              .flatMap[Throwable, Response[F[Throwable, ?]]] {
                case Some(basic) => routes(AuthedRequest(basic, req)).getOrElse(Response(Status.NotFound))
                case None        => F.pure(Response(Status.Unauthorized))
              }.catchAll(_ => F.pure(Response(Status.Unauthorized)))
          }
      }
  }

  def authenticate(request: Request[F[Throwable, ?]]): F[Throwable, Option[BasicContext]]
}
