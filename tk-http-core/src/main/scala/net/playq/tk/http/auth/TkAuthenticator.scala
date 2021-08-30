package net.playq.tk.http.auth

import cats.data.{Kleisli, OptionT}
import izumi.functional.bio.{F, IO2}
import org.http4s.*
import org.http4s.server.AuthMiddleware
import net.playq.tk.quantified.SyncThrowable

abstract class TkAuthenticator[F[+_, +_]: IO2: SyncThrowable, BasicContext] {
  /** should be similar to org.http4s.server.AuthMiddleware.apply * */
  final val authenticator: AuthMiddleware[F[Throwable, _], BasicContext] = {
    (routes: AuthedRoutes[BasicContext, F[Throwable, _]]) =>
      Kleisli {
        (req: Request[F[Throwable, _]]) =>
          OptionT.liftF {
            authenticate(req)
              .flatMap[Throwable, Response[F[Throwable, _]]] {
                case Some(basic) => routes(AuthedRequest(basic, req)).getOrElse(Response(Status.NotFound))
                case None        => F.pure(Response(Status.Unauthorized))
              }.catchAll(_ => F.pure(Response(Status.Unauthorized)))
          }
      }
  }

  def authenticate(request: Request[F[Throwable, _]]): F[Throwable, Option[BasicContext]]
}
