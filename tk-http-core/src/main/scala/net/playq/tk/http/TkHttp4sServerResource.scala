package net.playq.tk.http

import distage.Id
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Applicative2, F, IO2}
import izumi.fundamentals.platform.strings.IzString.*
import logstage.LogIO2
import net.playq.tk.http.TkHttp4sServerResource.{TkHttp4sMetrics, TkHttp4sServer}
import net.playq.tk.metrics.Metrics
import net.playq.tk.metrics.Metrics.MetricAttributes
import net.playq.tk.quantified.AsyncThrowable
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.headers.{Connection, `Content-Length`}
import org.http4s.metrics.{MetricsOps, TerminationType}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.{Router, Server}
import org.http4s.{Headers, HttpApp, HttpRoutes, InvalidBodyException, MessageFailure, Method, Request, Response, Status}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.util.chaining.*
import scala.util.control.NonFatal

final class TkHttp4sServerResource[F[+_, +_]: IO2: AsyncThrowable](
  interface: TkHttpServerBindTargetProvider,
  httpServices: Set[TkHttp4sService[F]],
  metrics: Metrics[F],
  logger: LogIO2[F],
  globalExecutionContext: ExecutionContext @Id("global"),
) extends Lifecycle.Of[F[Throwable, _], TkHttp4sServer]({
    def serverResource(): Lifecycle[F[Throwable, _], TkHttp4sServer] = {

      for {
        i      <- Lifecycle.liftF(interface.unlockAndGet)
        server <- BlazeServerBuilder[F[Throwable, _]]
          .withExecutionContext(globalExecutionContext)
          .bindHttp(i.port, i.bindingHost)
          .withHttpWebSocketApp(routes)
          .withLengthLimits(maxRequestLineLen = 5 * 1024 * 1024, maxHeadersLen = 5 * 1024 * 1024)
          .withIdleTimeout(2.minutes)
          .withSocketKeepAlive(true)
          .withMaxWebSocketBufferSize(None)
          .withBufferSize(1024 * 1024)
          .withSocketSendBufferSize(64 * 1024 * 1024)
          .withSocketReceiveBufferSize(64 * 1024 * 1024)
          .withServiceErrorHandler(serviceErrorHandler)
          .withoutBanner
          .resource
          .pipe(Lifecycle.fromCats(_))
          .pipe(withLog(i))
      } yield {
        TkHttp4sServer(server, i)
      }

    }

    def routes(ws: WebSocketBuilder2[F[Throwable, _]]): HttpApp[F[Throwable, _]] = {
      val routesList = httpServices.map(svc => svc.prefix -> svc.httpRoutes(ws)).toList
      val router     = TkHttp4sMetrics.wrap[F](metrics, Router(routesList*))
      router.orNotFound
    }

    def withLog[A](interface: TkHttpServerBindTarget): Lifecycle[F[Throwable, _], A] => Lifecycle[F[Throwable, _], A] = {
      (_: Lifecycle[F[Throwable, _], A]).wrapAcquire {
        f =>
          for {
            _   <- logger.info(s"Starting HTTP server with ${httpServices.size -> "services"}...")
            _   <- logger.info(s"${renderPrefixesList(httpServices) -> "root route prefixes"}")
            _   <- F.traverse(httpServices)(_.acquireLog)
            res <- f
            _   <- logger.info(s"HTTP Server started on: $interface")
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

    def renderPrefixesList(services: Set[TkHttp4sService[F]]): String = {
      val padSize = 30
      services.map {
        s =>
          val prefix           = s"${s.prefix}:"
          val paddedPrefix     = if (prefix.length > padSize) prefix else prefix + (" " * (padSize - prefix.length))
          val serviceClassName = s.getClass.getName
          paddedPrefix + serviceClassName
      }.toList.sorted.niceList()
    }

    def serviceErrorHandler: Request[F[Throwable, _]] => PartialFunction[Throwable, F[Throwable, Response[F[Throwable, _]]]] = req => {
      case error: MessageFailure =>
        logger
          .debug(s"Message failure handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr.getOrElse("<unknown>") -> "remoteAddress"}. $error")
          .as(error.toHttpResponse[F[Throwable, _]](req.httpVersion))
      case NonFatal(error: InvalidBodyException) =>
        logger
          .debug(s"Invalid body handling request: ${req.method} ${req.pathInfo} from ${req.remoteAddr.getOrElse("<unknown>") -> "remoteAddress"}. $error")
          .as(Response(Status.InternalServerError, req.httpVersion, Headers(Connection.close, `Content-Length`.zero)))
      case NonFatal(error) =>
        logger
          .warn(s"Error servicing request: ${req.method} ${req.pathInfo} from ${req.remoteAddr.getOrElse("<unknown>") -> "remoteAddress"}. $error")
          .as(Response(Status.InternalServerError, req.httpVersion, Headers(Connection.close, `Content-Length`.zero)))
    }

    serverResource()
  })

object TkHttp4sServerResource {
  final case class TkHttp4sServer(server: Server, interface: TkHttpServerBindTarget)

  /**
    * Ideally we should not report metrics with URL in the name here.
    * Since this middleware wraps WHOLE http server,
    * custom endpoint with dynamic URLs can cause an issues on the Prometheus/Influx side here.
    * Also, there is no actual need to report URLs in metrics like this.
    *
    * Note: explicitly setting [MetricAttributes.empty] as somehow attributes are
    *       being add to `recordHeadersTime` method (it's likely that it's reported when request is parsed in IRTHttpServer).
    */
  final case class TkHttp4sMetrics[F[+_, +_]: Applicative2](metrics: Metrics[F]) extends MetricsOps[F[Throwable, _]] {
    override def increaseActiveRequests(classifier: Option[String]): F[Throwable, Unit] = metrics.inc("http-requests", 1, MetricAttributes.empty)
    override def decreaseActiveRequests(classifier: Option[String]): F[Throwable, Unit] = metrics.dec("http-requests", 1, MetricAttributes.empty)

    /**
      * @param elapsed  It's not clear in what time units it is reported.
      *                 Still, from the Http4s source code, it looks like it is being reported in nanos.
      *                 Docs are silent about this.
      */
    override def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): F[Throwable, Unit] = {
      metrics.timerUpdate("http-requests-header", elapsed.nanos, MetricAttributes.empty)
    }

    /** Total time is recorded with MetricsSupport, wrapping http request execution.
      * Total time is recorded only for the IDL requests, and metrics are reported with full URL in the name.
      * (look into [[net.playq.tk.http.TkIdlHttpService]] and [[net.playq.tk.http.JmxMetricsSupport]])
      */
    override def recordTotalTime(method: Method, status: Status, elapsed: Long, classifier: Option[String]): F[Throwable, Unit] = F.unit

    override def recordAbnormalTermination(elapsed: Long, terminationType: TerminationType, classifier: Option[String]): F[Throwable, Unit] = {
      terminationType match {
        case TerminationType.Abnormal(_) => metrics.mark("http-requests-abnormal-termination", 1, MetricAttributes.empty)
        case TerminationType.Canceled    => metrics.mark("http-requests-canceled", 1, MetricAttributes.empty)
        case TerminationType.Error(_)    => metrics.mark("http-requests-error", 1, MetricAttributes.empty)
        case TerminationType.Timeout     => metrics.mark("http-requests-timeout", 1, MetricAttributes.empty)
      }
    }
  }

  object TkHttp4sMetrics {
    def wrap[F[+_, +_]: Applicative2: AsyncThrowable](metrics: Metrics[F], service: HttpRoutes[F[Throwable, _]]): HttpRoutes[F[Throwable, _]] = {
      org.http4s.server.middleware.Metrics[F[Throwable, _]](TkHttp4sMetrics(metrics))(service)
    }
  }
}
