package net.playq.tk.http

import cats.effect.Resource
import izumi.distage.model.definition.Id
import net.playq.tk.quantified.ConcurrentEffect2
import org.http4s.*
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.FollowRedirect

import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.chaining.*

trait TkHttp4sClient[F[_, _]] {
  private[http] type M[A] = F[Throwable, A]

  def fetch[A](req: Request[M], SSLContext: Option[SSLContext]     = None)(f: Response[M] => M[A]): M[A]
  def expect[A](req: Request[M], SSLContext: Option[SSLContext]    = None)(implicit ev1: EntityDecoder[M, A]): M[A]
  def expectString(req: Request[M], SSLContext: Option[SSLContext] = None): M[String]
  def expectBytes(req: Request[M], SSLContext: Option[SSLContext]  = None): M[Array[Byte]]
  def status(req: Request[M], SSLContext: Option[SSLContext]       = None): M[Status]
}

object TkHttp4sClient {

  final case class HttpClientCfg(timeout: FiniteDuration)

  def apply[F[+_, +_]: ConcurrentEffect2](client: Client[F[Throwable, _]]): TkHttp4sClient[F] = {
    new Impl[F](ExecutionContext.global, HttpClientCfg(20.seconds)) {
      override private[http] def newClient(sslContext: Option[SSLContext]): Resource[M, Client[M]] = {
        Resource.pure(client)
      }
    }
  }

  def of[F[+_, +_]](
    pf: PartialFunction[Request[F[Throwable, _]], F[Throwable, Response[F[Throwable, _]]]]
  )(implicit F: ConcurrentEffect2[F]
  ): TkHttp4sClient[F] = {
    TkHttp4sClient[F](
      Client.fromHttpApp(
        HttpApp[F[Throwable, _]](request => pf.applyOrElse(request, (_: Request[F[Throwable, _]]) => F.pure(Response.notFound)))
      )
    )
  }

  sealed class Impl[F[+_, +_]: ConcurrentEffect2](
    ec: ExecutionContext @Id("blockingIO"),
    cfg: HttpClientCfg,
  ) extends TkHttp4sClient[F] {

    private[http] def newClient(sslContext: Option[SSLContext]): Resource[M, Client[M]] = {
      BlazeClientBuilder[M](ec)
        .withMaxTotalConnections(1)
        .withResponseHeaderTimeout(cfg.timeout)
        .withRequestTimeout(cfg.timeout.plus(10.seconds))
        .withIdleTimeout(cfg.timeout.plus(20.seconds))
        .pipe(b => sslContext.fold(b.withDefaultSslContext)(b.withSslContext))
        .resource
        .map(FollowRedirect(1, _ => true))
    }

    override def fetch[A](req: Request[M], SSLContext: Option[SSLContext] = None)(f: Response[M] => M[A]): M[A] = {
      newClient(SSLContext).use(_.run(req).use(f))
    }

    override def expect[A](req: Request[M], SSLContext: Option[SSLContext] = None)(implicit ev1: EntityDecoder[M, A]): M[A] = {
      newClient(SSLContext).use(_.expect[A](req))
    }

    override def expectString(req: Request[M], SSLContext: Option[SSLContext] = None): M[String] = {
      expect[String](req, SSLContext)
    }

    override def expectBytes(req: Request[M], SSLContext: Option[SSLContext] = None): M[Array[Byte]] = {
      newClient(SSLContext).use(_.expect[Array[Byte]](req))
    }

    override def status(req: Request[M], SSLContext: Option[SSLContext] = None): M[Status] = {
      newClient(SSLContext).use(_.status(req))
    }
  }

}
