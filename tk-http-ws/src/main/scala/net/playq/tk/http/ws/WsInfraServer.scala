package net.playq.tk.http.ws

import cats.effect.std.Queue
import fs2.concurrent.SignallingRef
import fs2.{Pipe, Stream}
import izumi.functional.bio.{F, IO2, Primitives2}
import izumi.fundamentals.platform.strings.IzString.*
import logstage.LogIO2
import net.playq.tk.http.TkHttp4sService
import net.playq.tk.http.config.HttpSecretConfig
import net.playq.tk.http.ws.WsInfraException.ThrowableWsOps
import net.playq.tk.http.ws.model.{WsWireRequest, WsWireResponse}
import net.playq.tk.quantified.AsyncThrowable
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Authorization
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.{Credentials, HttpRoutes, Response}
import scodec.bits.ByteVector

import java.util.UUID
import scala.concurrent.duration.DurationInt

/** This websocket server is intended to handle custom non-IDL communication within the safe infrastructural boundaries
  *
  * (P2P communication between nodes, communication between browser sessions and the backend)
  *
  * If you are looking for the WS server used to serve IDL endpoints, check [[izumi.idealingua.runtime.rpc.http4s.HttpServer]]
  */
final class WsInfraServer[F[+_, +_]: IO2: AsyncThrowable: Primitives2](
  log: LogIO2[F],
  dsl: Http4sDsl[F[Throwable, _]],
  messageHandlers: Set[WsInfraMessageHandler[F, ?, ?]],
  secretConf: HttpSecretConfig,
) extends TkHttp4sService[F] {

  import dsl.*

  private val handlersByTag = messageHandlers.map(h => h.acceptable -> h).toMap

  override def acquireLog: F[Nothing, Unit] = {
    val handlers = handlersByTag.keys.toList.sorted.niceList()
    log.info(s"P2P WS server: Initialized with $handlers")
  }

  override def prefix: String = "/p2p/ws"

  override def httpRoutes: WebSocketBuilder2[F[Throwable, _]] => HttpRoutes[F[Throwable, _]] = ws =>
    HttpRoutes.of[F[Throwable, _]] {
      case r @ GET -> Root =>
        r.headers.get[Authorization] match {
          case Some(value) if validSecret(value) => createWsConnection(ws)
          case _                                 => Forbidden()
        }
    }

  private def validSecret(header: Authorization): Boolean = {
    header.credentials match {
      case Credentials.Token(authScheme, token) => authScheme.toString == secretConf.scheme && token == secretConf.secret
      case _                                    => false
    }
  }

  private def pingStream: Stream[F[Throwable, _], WebSocketFrame] = {
    Stream
      .awakeEvery[F[Throwable, _]](1.second)
      .evalMap(_ => log.debug("Infra WS Server: Sending ping frame.").as(WebSocketFrame.Ping()))
  }

  private def processClientFrames(queue: Queue[F[Throwable, _], Option[WebSocketFrame]]): Pipe[F[Throwable, _], WebSocketFrame, Unit] = {
    (inputStream: Stream[F[Throwable, _], WebSocketFrame]) =>
      for {
        requestSignals  <- Stream.eval(F.mkRefM(Map.empty[UUID, SignallingRef[F[Throwable, _], Boolean]]))
        closeAllRequests = requestSignals.update_(F.traverse(_) { case (_, s) => s.set(true) }.as(Map.empty))
        // process input stream in parallel to not block other requests
        _ <- inputStream.parEvalMapUnbounded {
          case binary: WebSocketFrame.Binary =>
            F.syncThrowable(WsWireRequest.fromBytes(binary.data.toArray)).leftMapCodecFailure.flatMap {
              case r @ WsWireRequest.RequestMessage(requestId, _, _) =>
                for {
                  signal <- SignallingRef.of(false)
                  _      <- requestSignals.update_(m => F.sync(m ++ Map(requestId -> signal)))
                  _      <- produceResponseStream(r).evalMap(m => queue.offer(Some(m))).interruptWhen(signal).compile.drain
                  _      <- log.debug(s"Infra WS Server: stream for request $requestId finished.")
                } yield ()
              case WsWireRequest.RequestClose(requestId) =>
                requestSignals.update_(m => F.traverse(m.get(requestId))(_.set(true)).as(m.removed(requestId)))
            }.catchAll {
              error =>
                // close WS with error once the critical error appeared
                for {
                  _            <- log.crit(s"Infra WS Server: can not process messages due to a critical error. $error.")
                  closeMessage <- F.fromEither(WebSocketFrame.Close(1011, s"Can not process messages due to a critical error. Error: ${error.getMessage}"))
                  _            <- queue.offer(Some(closeMessage))
                  _            <- queue.offer(None)
                  _            <- closeAllRequests
                } yield ()
            }

          case WebSocketFrame.Close(_) =>
            for {
              _ <- log.debug("Infra WS Server: Got closing frame.")
              _ <- queue.offer(None)
              _ <- closeAllRequests
            } yield ()
          case WebSocketFrame.Pong(_) =>
            // by default AsyncHttp will filter ping-pong frames
            // this behaviour may be changed in WS builder: `ws.withFilterPingPongs(false)`
            log.debug("Infra WS Server: Got pong frame.")
          case _ =>
            log.warn("Infra WS Server: Got unknown message type. Ignoring")
        }
      } yield ()
  }

  private def produceResponseStream(wireMessage: WsWireRequest.RequestMessage): Stream[F[Throwable, _], WebSocketFrame.Binary] = {
    {
      for {
        handler <- Stream.fromEither(handlersByTag.get(wireMessage.tag).toRight(WsInfraException.HandlerNotFound(wireMessage.tag)))
        res     <- handler.handleWire(wireMessage)(using IO2[F], log, AsyncThrowable[F])
      } yield res
    }.handleErrorWith {
      error =>
        for {
          _ <- Stream.eval(log.error(s"Infra WS Server: stream errored. $error"))
        } yield WsWireResponse.ResponseErrored(wireMessage.requestId, error)
    }.evalMap(m => F.syncThrowable(WebSocketFrame.Binary(ByteVector(m.toBytes))).leftMapCodecFailure)
  }

  private def createWsConnection(ws: WebSocketBuilder2[F[Throwable, _]]): F[Throwable, Response[F[Throwable, _]]] = {
    for {
      outQueue <- Queue.unbounded[F[Throwable, _], Option[WebSocketFrame]]
      outStream = Stream.fromQueueNoneTerminated(outQueue).merge(pingStream)
      inStream  = processClientFrames(outQueue)
      response <- ws.build(outStream, inStream)
    } yield response
  }
}
