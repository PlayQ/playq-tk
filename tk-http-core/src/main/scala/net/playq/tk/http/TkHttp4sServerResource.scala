package net.playq.tk.http

import distage.Id
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2}
import izumi.fundamentals.platform.strings.IzString._
import logstage.LogIO2
import TkHttp4sServerResource.TgHttp4sServer
import org.http4s.HttpApp
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import org.http4s.syntax.kleisli._
import net.playq.tk.quantified.{ConcurrentEffect2, TimerThrowable}

import scala.concurrent.ExecutionContext
import scala.util.chaining._

final class TkHttp4sServerResource[F[+_, +_]: IO2: ConcurrentEffect2: TimerThrowable, BaseContext, FullContext, ClientId](
  interface: TkHttpInterface,
  restServices: Set[TkHttp4sService[F, BaseContext]],
  logger: LogIO2[F],
  globalExecutionContext: ExecutionContext @Id("global"),
) extends Lifecycle.Of[F[Throwable, ?], TgHttp4sServer[F, BaseContext, FullContext]]({
    def resource: Lifecycle[F[Throwable, ?], TgHttp4sServer[F, BaseContext, FullContext]] = {
      BlazeServerBuilder[F[Throwable, ?]](globalExecutionContext)
        .bindHttp(interface.port, interface.bindingHost)
        .withHttpApp(routes)
        .withLengthLimits(maxRequestLineLen = 64 * 1024, maxHeadersLen = 64 * 1024)
        .resource
        .pipe(Lifecycle.fromCats(_))
        .pipe(withLog(interface))
        .map(TgHttp4sServer[F, BaseContext, FullContext])
    }

    def routes: HttpApp[F[Throwable, ?]] = {
      val all = restServices.map(svc => svc.prefix -> svc.httpRoutes).toList
      Router(all: _*).orNotFound
    }

    def withLog[A](interface: TkHttpInterface): Lifecycle[F[Throwable, ?], A] => Lifecycle[F[Throwable, ?], A] = {
      (_: Lifecycle[F[Throwable, ?], A]).wrapAcquire {
        f =>
          for {
            _   <- logger.info("Starting HTTP server...")
            _   <- F.traverse(restServices)(_.acquireLog)
            _   <- logger.info(s"${restServices.toList.map(_.prefix).sorted.niceList() -> "REST prefixes"}")
            _   <- F.syncThrowable(interface.unlockPort)
            res <- f
            _   <- logger.info(s"Http Server started on: $interface")
          } yield res
      }.wrapRelease {
        (r, s) =>
          for {
            _ <- logger.info("Stopping HTTP server...")
            _ <- r(s)
            _ <- logger.info("HTTP server has been stopped...")
          } yield ()
      }
    }
    resource
  })

object TkHttp4sServerResource {
  final case class TgHttp4sServer[F[_, _], BaseContext, FullContext](server: Server[F[Throwable, ?]])
}
