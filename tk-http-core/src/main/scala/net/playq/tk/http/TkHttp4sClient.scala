package net.playq.tk.http

import cats.effect.Resource
import distage.Lifecycle
import fs2.io.file.Files
import izumi.distage.model.definition.Id
import izumi.functional.bio.{F, IO2}
import net.playq.tk.quantified.AsyncThrowable
import net.playq.tk.util.ClosableFile
import org.http4s.*
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.client.middleware.FollowRedirect

import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.util.chaining.*

trait TkHttp4sClient[F[_, _]] {
  def fetch[A](req: Request[F[Throwable, _]])(f: Response[F[Throwable, _]] => F[Throwable, A]): F[Throwable, A]
  def stream(req: Request[F[Throwable, _]]): fs2.Stream[F[Throwable, _], Response[F[Throwable, _]]]
  def expect[A](req: Request[F[Throwable, _]])(implicit ev1: EntityDecoder[F[Throwable, _], A]): F[Throwable, A]
  def expectString(req: Request[F[Throwable, _]]): F[Throwable, String]
  def expectBytes(req: Request[F[Throwable, _]]): F[Throwable, Array[Byte]]
  def expectFile(req: Request[F[Throwable, _]], fileSuffix: String = ".tmp"): Lifecycle[F[Throwable, _], ClosableFile]
  def status(req: Request[F[Throwable, _]]): F[Throwable, Status]
}

object TkHttp4sClient {

  final case class HttpClientCfg(timeout: FiniteDuration)

  def apply[F[+_, +_]: IO2: AsyncThrowable](client: Client[F[Throwable, _]]): TkHttp4sClient[F] = {
    new Impl[F](client)
  }

  def of[F[+_, +_]](
    pf: PartialFunction[Request[F[Throwable, _]], F[Throwable, Response[F[Throwable, _]]]]
  )(implicit
    IO2: IO2[F],
    AF: AsyncThrowable[F],
  ): TkHttp4sClient[F] = {
    TkHttp4sClient[F](
      Client.fromHttpApp(
        HttpApp[F[Throwable, _]](request => pf.applyOrElse(request, (_: Request[F[Throwable, _]]) => IO2.pure(Response.notFound)))
      )
    )
  }

  final class MkImpl[F[+_, +_]: IO2: AsyncThrowable](
    ec: ExecutionContext @Id("blockingIO"),
    cfg: HttpClientCfg,
  ) extends Lifecycle.Of[F[Throwable, _], TkHttp4sClient[F]]({

      def sharedClient(sslContext: Option[SSLContext]): Resource[F[Throwable, _], Client[F[Throwable, _]]] = {
        BlazeClientBuilder[F[Throwable, _]]
          .withExecutionContext(ec)
          .withMaxTotalConnections(1024)
          .withBufferSize(1024 * 1024)
          .withResponseHeaderTimeout(cfg.timeout)
          .withRequestTimeout(cfg.timeout.plus(10.seconds))
          .withIdleTimeout(cfg.timeout.plus(20.seconds))
          .withSocketKeepAlive(true)
          .pipe(b => sslContext.fold(b.withDefaultSslContext)(b.withSslContext))
          .resource
          .map(FollowRedirect(1, _ => true))
      }

      Lifecycle.fromCats(sharedClient(sslContext = None)).map {
        client => new Impl[F](client)
      }
    })

  final class Impl[F[+_, +_]: IO2: AsyncThrowable](
    client: Client[F[Throwable, _]]
  ) extends TkHttp4sClient[F] {

    override def fetch[A](req: Request[F[Throwable, _]])(f: Response[F[Throwable, _]] => F[Throwable, A]): F[Throwable, A] = {
      client.run(req).use(f)
    }

    override def stream(req: Request[F[Throwable, _]]): fs2.Stream[F[Throwable, _], Response[F[Throwable, _]]] = {
      client.stream(req)
    }

    override def expect[A](req: Request[F[Throwable, _]])(implicit ev1: EntityDecoder[F[Throwable, _], A]): F[Throwable, A] = {
      client.expect[A](req)
    }

    override def expectString(req: Request[F[Throwable, _]]): F[Throwable, String] = {
      client.expect[String](req)
    }

    override def expectBytes(req: Request[F[Throwable, _]]): F[Throwable, Array[Byte]] = {
      client.expect[Array[Byte]](req)
    }

    override def expectFile(req: Request[F[Throwable, _]], fileSuffix: String): Lifecycle[F[Throwable, _], ClosableFile] = {
      for {
        file     <- ClosableFile.resourceTempFile[F]("managed_file", fileSuffix)
        response <- Lifecycle.fromCats[F[Throwable, _], Response[F[Throwable, _]]](client.run(req))
        _        <- Lifecycle.liftF(F.when(response.status != Status.Ok)(F.fail(new RuntimeException(s"Unexpected status: ${response.status}."))))
        _        <- Lifecycle.liftF {
          response.body
            .pipe(Files.forAsync[F[Throwable, _]].writeAll(fs2.io.file.Path.fromNioPath(file.absolutePath)))
            .compile.drain
        }
      } yield file
    }

    override def status(req: Request[F[Throwable, _]]): F[Throwable, Status] = {
      client.status(req)
    }
  }

}
