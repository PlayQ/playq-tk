package net.playq.tk.http

import io.circe.Printer
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Clock2, Entropy1, F, IO2, Primitives2, Temporal2, UnsafeRun2}
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.strings.IzString.*
import izumi.idealingua.runtime.rpc.http4s.IRTContextServices
import izumi.idealingua.runtime.rpc.http4s.context.{HttpContextExtractor, WsContextExtractor}
import izumi.idealingua.runtime.rpc.http4s.ws.WsSessionsStorage
import logstage.LogIO2
import net.playq.tk.http.config.TkIdlHttpServiceConfig
import net.playq.tk.metrics.*
import net.playq.tk.metrics.base.MetricDef
import net.playq.tk.metrics.base.MetricDef.{MetricCounter, MetricMeter, MetricTimer}
import net.playq.tk.metrics.macrodefs.MacroMetricSaver
import net.playq.tk.quantified.AsyncThrowable
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder2

final class TkIdlHttpService[F[+_, +_]: IO2: Temporal2: Primitives2: AsyncThrowable: UnsafeRun2, AuthCtx](
  serviceConfig: TkIdlHttpServiceConfig,
  metrics: Metrics[F],
  clock: Clock2[F],
  metricsMiddleware: MetricsSupport[F[Throwable, _]],
  // IDL HttpServer params
  contextServices: Set[IRTContextServices.AnyContext[F, AuthCtx]],
  httpContextExtractor: HttpContextExtractor[AuthCtx],
  wsContextExtractor: WsContextExtractor[AuthCtx],
  wsSessionsStorage: WsSessionsStorage[F, AuthCtx],
  dsl: Http4sDsl[F[Throwable, _]],
  logger: LogIO2[F],
  entropy1: Entropy1[Identity],
  printer: Printer,
) extends Lifecycle.NoClose[F[Throwable, _], TkHttp4sService[F]] {

  override def acquire: F[Throwable, TkHttp4sService[F]] = F.sync {
    new TkHttp4sService[F] {
      override def prefix: String               = serviceConfig.prefix
      override def acquireLog: F[Nothing, Unit] = {
        for {
          _ <- logger.info(s"About to initialize Idealingua IDL server (HTTP+WS) with ${contextServices.size -> "security contexts"}")
          _ <- F.traverse_(contextServices.toList.sortBy(_.name)) {
            contextService =>
              val methods  = contextService.serverMuxer.methods.keySet
              val services = methods.map(_.service.value).toList.sorted.niceList()
              logger.info(s"${contextService.name -> "security context"} with $prefix has ${methods.size -> "endpoints"} provided by $services")
          }
        } yield {}

      }
      override def httpRoutes: WebSocketBuilder2[F[Throwable, _]] => HttpRoutes[F[Throwable, _]] = {
        val httpServer = new TkIdlHttpServer(
          metrics              = metrics,
          clock                = clock,
          contextServices      = contextServices,
          httpContextExtractor = httpContextExtractor,
          wsContextExtractor   = wsContextExtractor,
          wsSessionsStorage    = wsSessionsStorage,
          dsl                  = dsl,
          logger               = logger,
          printer              = printer,
          entropy1             = entropy1,
        )
        metricsMiddleware.wrap(httpServer.service)
      }
    }
  }
}

object TkIdlHttpService {

  object TkMetricLabels {
    final val activeUsers   = "websocket/users/online"
    final val wsLatency     = "websocket/latency"
    final val wsRpcFailure  = "websocket/error/rpc"
    final val wsBuzzFailure = "websocket/error/buzzer"
    final val wsCritical    = "websocket/error/critical"
    final val wsRpcUnknown  = "websocket/error/unknown"
  }

  def tkMetrics(roleId: String): List[MetricDef] = {
    List(
      MetricTimer(roleId, TkMetricLabels.wsLatency, 0.0),
      MetricCounter(MacroMetricSaver.defaultMetricRole, TkMetricLabels.activeUsers, 0),
      MetricMeter(roleId, TkMetricLabels.wsRpcFailure, 0L),
      MetricMeter(roleId, TkMetricLabels.wsBuzzFailure, 0L),
      MetricMeter(roleId, TkMetricLabels.wsCritical, 0L),
      MetricMeter(roleId, TkMetricLabels.wsRpcUnknown, 0L),
    )
  }
}
