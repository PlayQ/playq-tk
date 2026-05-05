package net.playq.tk.http

import io.circe
import io.circe.{Json, Printer}
import izumi.functional.bio.Exit.{Error, Interruption, Success, Termination}
import izumi.functional.bio.{Clock2, Entropy1, Exit, F, IO2, Primitives2, Temporal2, UnsafeRun2}
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.functional.Identity
import izumi.idealingua.runtime.rpc.*
import izumi.idealingua.runtime.rpc.http4s.*
import izumi.idealingua.runtime.rpc.http4s.HttpServer.ServerWsRpcHandler
import izumi.idealingua.runtime.rpc.http4s.context.{HttpContextExtractor, WsContextExtractor}
import izumi.idealingua.runtime.rpc.http4s.ws.*
import logstage.LogIO2
import net.playq.tk.http.TkIdlHttpService.TkMetricLabels
import net.playq.tk.metrics.Metrics
import net.playq.tk.metrics.macrodefs.MacroMetricBase.discarded.*
import net.playq.tk.quantified.AsyncThrowable
import net.playq.tk.util.ServiceUnavailableException
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Request, Response, Status}

import java.time.ZonedDateTime
import java.util.concurrent.{RejectedExecutionException, TimeUnit}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class TkIdlHttpServer[F[+_, +_]: IO2: Temporal2: AsyncThrowable: Primitives2: UnsafeRun2, AuthCtx](
  metrics: Metrics[F],
  clock: Clock2[F],
  // IDL HttpServer params
  contextServices: Set[IRTContextServices.AnyContext[F, AuthCtx]],
  httpContextExtractor: HttpContextExtractor[AuthCtx],
  wsContextExtractor: WsContextExtractor[AuthCtx],
  wsSessionsStorage: WsSessionsStorage[F, AuthCtx],
  dsl: Http4sDsl[F[Throwable, _]],
  logger: LogIO2[F],
  printer: Printer,
  entropy1: Entropy1[Identity],
) extends HttpServer[F, AuthCtx](contextServices, httpContextExtractor, wsContextExtractor, wsSessionsStorage, dsl, logger, printer, entropy1) {
  import dsl.*

  private[this] val defaultContentType: `Content-Type` = `Content-Type`(MediaType.application.json)

  override protected val wsHeartbeatTimeout: FiniteDuration  = 1.minute
  override protected val wsHeartbeatInterval: FiniteDuration = 10.seconds

  override protected def handleHttpResult(
    request: Request[F[Throwable, _]],
    method: IRTMethodId,
  )(result: Exit[Throwable, Json]
  ): F[Throwable, Response[F[Throwable, _]]] = {
    result match {
      case Success(res) =>
        Ok(printer.print(res)).map(_.withContentType(defaultContentType))

      case Error(err: IRTMissingHandlerException, _) =>
        logger.warn(s"HTTP Request execution failed - no method handler for $method: $err") *>
        NotFound()

      case Error(error: circe.Error, trace) =>
        logger.warn(s"HTTP Request execution failed - parsing failure while handling $method:\n${error.getMessage -> "error"}\n$trace") *>
        BadRequest()

      case Error(error: IRTDecodingException, trace) =>
        logger.warn(s"HTTP Request execution failed - parsing failure while handling $method:\n$error\n$trace") *>
        BadRequest()

      case Error(error: IRTLimitReachedException, trace) =>
        logger.debug(s"HTTP Request failed - request limit reached $method:\n$error\n$trace") *>
        TooManyRequests()

      case Error(error: IRTUnathorizedRequestContextException, trace) =>
        logger.warn(s"HTTP Request failed - unauthorized $method call:\n$error\n$trace") *>
        F.pure(Response(status = Status.Unauthorized))

      // handle service unavailable if appears in error
      case Error(error: ServiceUnavailableException, trace) =>
        logger.warn(s"HTTP Request execution failed because of service unavailable exception $method:\n$error\n$trace") *>
        ServiceUnavailable(error.message)

      case Error(error, trace) =>
        logger.warn(s"HTTP Request unexpectedly failed while handling $method:\n$error\n$trace") *>
        InternalServerError()

      case Termination(_, (cause: IRTHttpFailureException) :: _, trace) =>
        logger.error(s"HTTP Request rejected - $method, $request:\n$cause\n$trace") *>
        F.pure(Response(status = cause.status))

      case Termination(_, (cause: RejectedExecutionException) :: _, trace) =>
        logger.warn(s"HTTP Request rejected - Not enough capacity to handle $method:\n$cause\n$trace") *>
        TooManyRequests()

      // handle service unavailable if appears in termination (like from the IDL client)
      case Termination(cause: ServiceUnavailableException, _, trace) =>
        logger.debug(s"HTTP Request execution failed, termination with unavailable services, $method, $request:\n$cause\n$trace") *>
        ServiceUnavailable(cause.message)

      // handle service unavailable if appears in termination
      case Termination(cause, errors, trace) =>
        NEList.from(errors.collectFirst { case e: ServiceUnavailableException => e }) match {
          case Some(serviceUnavailableErrors) =>
            logger.error(s"HTTP Request execution failed, termination with unavailable services, $method, $request:\n$cause\n$trace") *>
            ServiceUnavailable(serviceUnavailableErrors.mkString("\n"))
          case None =>
            logger.error(s"HTTP Request execution failed, termination, $method, $request:\n$cause\n$trace") *>
            InternalServerError()
        }

      case Interruption(cause, _, trace) =>
        logger.error(s"HTTP Request unexpectedly interrupted while handling $method:\n$cause\n$trace") *>
        InternalServerError()
    }
  }

  override protected def onWsConnected(authContext: AuthCtx): F[Throwable, Unit] = {
    metrics.inc(TkMetricLabels.activeUsers) *> super.onWsConnected(authContext)
  }

  override protected def onWsDisconnected(authContext: AuthCtx): F[Throwable, Unit] = {
    metrics.dec(TkMetricLabels.activeUsers) *> super.onWsDisconnected(authContext)
  }

  override protected def onWsHeartbeat(requestTime: ZonedDateTime): F[Throwable, Unit] = {
    for {
      _      <- super.onWsHeartbeat(requestTime)
      now    <- clock.nowZoned()
      curTime = (now.toEpochSecond - requestTime.toEpochSecond) / 2
      _      <- metrics.timerUpdate(TkMetricLabels.wsLatency, FiniteDuration(curTime, TimeUnit.SECONDS))
    } yield ()
  }

  /** Add metrics only for rpc request with given service and method names */
  override protected def wsHandler(clientSession: WsClientSession[F, AuthCtx]): WsRpcHandler[F, AuthCtx] = {
    new ServerWsRpcHandler(clientSession, serverMuxer, wsContextExtractor, logger) {
      override def processRpcPacket(packet: RpcPacket): F[Throwable, Option[RpcPacket]] = {
        for {
          requestCtx <- updateRequestCtx(packet)
          response   <- packet match {
            // auth
            case RpcPacket(RPCPacketKind.RpcRequest, None, _, _, _, _, _) =>
              handleAuthRequest(requestCtx, packet)

            case RpcPacket(RPCPacketKind.RpcResponse, None, _, Some(ref), _, _, _) =>
              handleAuthResponse(ref, packet)

            // rpc
            case RpcPacket(RPCPacketKind.RpcRequest, Some(data), Some(id), _, Some(service), Some(method), _) =>
              handleWsRequest(IRTMethodId(IRTServiceId(service), IRTMethodName(method)), requestCtx, data)(
                onSuccess = RpcPacket.rpcResponse(id, _),
                onFail    = RpcPacket.rpcFail(Some(id), _),
              )

            case RpcPacket(RPCPacketKind.RpcResponse, Some(data), _, Some(ref), _, _, _) =>
              clientSession.responseWithData(ref, data).as(None)

            case RpcPacket(RPCPacketKind.RpcFail, data, _, Some(ref), _, _, _) =>
              metrics.mark(TkMetricLabels.wsRpcFailure) *>
              clientSession.responseWith(ref, RawResponse.BadRawResponse(data)).as(None)

            // buzzer
            case RpcPacket(RPCPacketKind.BuzzRequest, Some(data), Some(id), _, Some(service), Some(method), _) =>
              handleWsRequest(IRTMethodId(IRTServiceId(service), IRTMethodName(method)), requestCtx, data)(
                onSuccess = RpcPacket.buzzerResponse(id, _),
                onFail    = RpcPacket.buzzerFail(Some(id), _),
              )

            case RpcPacket(RPCPacketKind.BuzzResponse, Some(data), _, Some(ref), _, _, _) =>
              clientSession.responseWithData(ref, data).as(None)

            case RpcPacket(RPCPacketKind.BuzzFailure, data, _, Some(ref), _, _, _) =>
              metrics.mark(TkMetricLabels.wsBuzzFailure) *>
              clientSession.responseWith(ref, RawResponse.BadRawResponse(data)).as(None)

            // critical failures
            case RpcPacket(RPCPacketKind.Fail, data, _, Some(ref), _, _, _) =>
              metrics.mark(TkMetricLabels.wsCritical) *>
              clientSession.responseWith(ref, RawResponse.BadRawResponse(data)).as(None)

            case RpcPacket(RPCPacketKind.Fail, data, _, None, _, _, _) =>
              metrics.mark(TkMetricLabels.wsCritical) *>
              logger.error(s"WS request failed: Unknown RPC failure: $data.").as(None): F[Throwable, Option[RpcPacket]]

            // unknown
            case packet =>
              metrics.mark(TkMetricLabels.wsRpcUnknown) *>
              logger
                .error(s"WS request failed: No buzzer client handler for $packet")
                .as(Some(RpcPacket.rpcCritical("No buzzer client handler", packet.ref))): F[Throwable, Option[RpcPacket]]
          }
        } yield response
      }
    }
  }
}
