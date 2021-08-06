package net.playq.tk.http

import io.circe.Printer
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Clock2, F, IO2}
import izumi.fundamentals.platform.strings.IzString._
import izumi.idealingua.runtime.rpc._
import izumi.idealingua.runtime.rpc.http4s._
import izumi.logstage.api.IzLogger
import logstage.LogIO2
import TkIdlServerBindings.TkMetricLabels
import net.playq.tk.metrics._
import net.playq.tk.metrics.base.MetricDef
import net.playq.tk.metrics.base.MetricDef.{MetricCounter, MetricTimer}
import net.playq.tk.metrics.macrodefs.MacroMetricBase.discarded._
import net.playq.tk.metrics.macrodefs.MacroMetricSaver
import net.playq.tk.metrics.MetricsSupport
import net.playq.tk.http.auth.TkAuthenticator
import net.playq.tk.http.config.HttpInterfaceConfig
import org.http4s.headers.`Content-Type`
import org.http4s.{HttpRoutes, MediaType, Response}

import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

final class TkIdlServerBindings[F[+_, +_]: IO2, BaseContext, FullContext, ClientId](
  interfaceConfig: HttpInterfaceConfig,
  logger: LogIO2[F],
  clock: Clock2[F],
  metrics: Metrics[F],
  metricsMiddleware: MetricsSupport[F[Throwable, ?]],
  services: Set[IRTWrappedService[F, FullContext]],
  // http server deps
  serverMuxer: IRTServerMultiplexor[F, BaseContext],
  clientMuxer: IRTClientMultiplexor[F],
  authenticator: TkAuthenticator[F, BaseContext],
  wsContextProvider: WsContextProvider[F, BaseContext, ClientId],
  wsSessionStorage: WsSessionsStorage[F, ClientId, BaseContext],
  wsSessionListeners: Set[WsSessionListener[F, ClientId]],
  izLogger: IzLogger,
  printer: Printer,
  private[TkIdlServerBindings] val rt: TkHttp4sRuntime[F, BaseContext, FullContext, ClientId],
) extends Lifecycle.NoClose[F[Throwable, ?], TkHttp4sService[F, BaseContext]] {

  private[this] val defaultContentType: `Content-Type` = `Content-Type`(MediaType.application.json)

  override def acquire: F[Throwable, TkHttp4sService[F, BaseContext]] = F.sync {
    new TkHttp4sService[F, BaseContext] {
      override def prefix: String = interfaceConfig.apiVersion.getOrElse("/v2")
      override def acquireLog: F[Nothing, Unit] = {
        for {
          _ <- logger.info(s"Going to expose ${services.size -> "endpoints"} IDL endpoints on ${prefix -> "Api Version"} with HTTP...")
          _ <- logger.info(s"${services.toList.map(_.serviceId.value).sorted.niceList() -> "IDL services"}")
        } yield ()
      }
      override def httpRoutes: HttpRoutes[F[Throwable, ?]] = metricsMiddleware.wrap(httpServer().service)
    }
  }

  private[this] def httpServer(): HttpServer[rt.type] = {
    new HttpServer[rt.type](rt, serverMuxer, clientMuxer, authenticator.authenticator, wsContextProvider, wsSessionStorage, wsSessionListeners.toSeq, izLogger, printer) {
      override protected def run(
        context: HttpRequestContext[F[Throwable, ?], BaseContext],
        body: String,
        method: IRTMethodId,
      ): F[Throwable, Response[F[Throwable, ?]]] = {
        withMetrics(method) {
          super.run(context, body, method).map(_.withContentType(defaultContentType))
        }
      }

      override def onHeartbeat(requestTime: ZonedDateTime): F[Throwable, Unit] = {
        for {
          now    <- clock.now()
          curTime = (now.toEpochSecond - requestTime.toEpochSecond) / 2
          _      <- metrics.timerUpdate(TkMetricLabels.wsLatency, FiniteDuration(curTime, TimeUnit.SECONDS))
        } yield ()
      }

      override protected def onWsClosed(): c.MonoIO[Unit] = {
        metrics.dec(TkMetricLabels.activeUsers)
      }

      override protected def onWsOpened(): c.MonoIO[Unit] = {
        metrics.inc(TkMetricLabels.activeUsers)
      }

      override protected def onWsUpdate(maybeNewId: Option[ClientId], old: WsClientId[ClientId]): c.MonoIO[Unit] = F.unit

      /** Add metrics only for rpc request with given service and method names */
      override protected def respond(context: WebsocketClientContextImpl[rt.type], input: RpcPacket): F[Throwable, Option[RpcPacket]] = {
        (input.service, input.method) match {
          case (Some(service), Some(method)) =>
            val methodId = IRTMethodId(IRTServiceId(service), IRTMethodName(method))
            withMetrics(methodId)(super.respond(context, input))
          case _ => super.respond(context, input)
        }
      }

      private def withMetrics[T](method: IRTMethodId)(v: => F[Throwable, T]): F[Throwable, T] = {
        metrics
          .withTimer(name(method.toString, "/time"))(v)
          .tapError(_ => metrics.mark(name(method.toString, "/errors")))
      }

      private def name(parts: String*): String = {
        "service/" ++ parts.mkString
      }
    }
  }
}

object TkIdlServerBindings {

  object TkMetricLabels {
    final val activeUsers = "websocket/users/online"
    final val wsLatency   = "websocket/latency"
  }

  def tkMetrics(roleId: String): List[MetricDef] = {
    List(MetricTimer(roleId, TkMetricLabels.wsLatency, 0.0), MetricCounter(MacroMetricSaver.defaultMetricRole, TkMetricLabels.activeUsers, 0))
  }
}
