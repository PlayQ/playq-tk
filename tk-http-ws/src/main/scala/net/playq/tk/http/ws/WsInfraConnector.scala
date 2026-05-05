package net.playq.tk.http.ws

import cats.effect.std.Queue
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, Entropy2, Exit, F, Fork2, Monad2, Primitives2, RefM2, Temporal2, UnsafeRun2}
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import logstage.{IzLogger, LogIO2}
import net.playq.tk.http.config.HttpSecretConfig
import net.playq.tk.http.ws.model.WsWireResponse
import net.playq.tk.quantified.AsyncThrowable
import org.asynchttpclient.ws.{WebSocket, WebSocketListener, WebSocketUpgradeHandler}
import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig}
import org.http4s.headers.Authorization
import org.http4s.{Credentials, Uri}
import org.typelevel.ci.CIString

import java.util.UUID
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.jdk.CollectionConverters.*
import scala.util.Try

final class WsInfraConnector[F[+_, +_]: Async2: UnsafeRun2: Fork2: Temporal2: AsyncThrowable: Primitives2](
  client: AsyncHttpClient,
  logger: LogIO2[F],
  izLogger: IzLogger,
  secretConf: HttpSecretConfig,
  entropy2: Entropy2[F],
) {
  def connect(uri: Uri): Lifecycle[F[Throwable, _], WsInfraConnection[F]] = {
    for {
      requestsQueues <- Lifecycle.liftF(F.mkRefM(Map.empty[UUID, Queue[F[Throwable, _], WsWireResponse]]))
      listener       <- Lifecycle.liftF(F.syncThrowable(makeListener(requestsQueues)))
      handler        <- Lifecycle.liftF(F.syncThrowable(new WebSocketUpgradeHandler(List(listener).asJava)))
      wsUri           = uri.renderString.replace("http", "ws") + "/p2p/ws"
      nettySocket    <- Lifecycle
        .liftF(
          logger.debug(s"WS Client: opening WS connection to $uri.") *>
          F.fromFutureJava(
            client
              .prepareGet(wsUri)
              .addHeader(Authorization.name.toString, s"${Credentials.Token(CIString(secretConf.scheme), secretConf.secret)}")
              .execute(handler).toCompletableFuture
          )
        ).beforeRelease(_ => logger.debug(s"WS Client: closing WS connection to $uri."))
      connection <- WsInfraConnection.nettyResource(nettySocket, requestsQueues, izLogger, entropy2)
    } yield connection
  }

  private def makeListener(
    requestsQueues: RefM2[F, Map[UUID, Queue[F[Throwable, _], WsWireResponse]]]
  ): WebSocketListener = new WebSocketListener {
    private val state = WsInfraConnector.SocketListenerState(requestsQueues, new AtomicReference[WebSocket], new AtomicBoolean(false))
    private def broadcastResponse(rsp: UUID => WsWireResponse): Unit = {
      UnsafeRun2[F].unsafeRunAsync {
        requestsQueues.get.flatMap(F.traverse_(_) { case (uuid, q) => q.offer(rsp(uuid)) })
      } {
        case Exit.Success(_) => ()
        case err             => izLogger.error(s"WS Client: broadcast message failed: $err")
      }
    }
    override def onOpen(websocket: WebSocket): Unit = {
      izLogger.debug(s"WS Client: opened a web socket connection.")
      state.socket.set(websocket)
    }
    override def onClose(websocket: WebSocket, code: Int, reason: String): Unit = {
      if (state.finished.get()) {
        izLogger.debug(s"WS Client: connection closed successfully: $code $reason")
        broadcastResponse(uuid => WsWireResponse.ResponseClose(uuid, 0))
      } else {
        if (code == 1000) {
          izLogger.debug(s"WS Client: connection closed by server: $code $reason")
          broadcastResponse(uuid => WsWireResponse.ResponseClose(uuid, 0))
        } else {
          izLogger.error(s"WS Client: connection closed by server with error: $code $reason")
          broadcastResponse(uuid => WsWireResponse.ResponseErrored(uuid, reason, ""))
        }
        state.closeAll
      }
    }
    override def onError(t: Throwable): Unit = {
      val remoteAddress = state.socket.get().getRemoteAddress
      val localAddress  = state.socket.get().getLocalAddress
      izLogger.error(s"WS Client: connection failed with ${t.getMessage -> "error"}: remote address: $remoteAddress local address: $localAddress")
      broadcastResponse(WsWireResponse.ResponseErrored(_, t.getMessage, t.stacktraceString))
      state.closeAll
    }
    override def onBinaryFrame(frame: Array[Byte], finalFragment: Boolean, rsv: Int): Unit = {
      val message = Try(WsWireResponse.fromBytes(frame)).toOption
      izLogger.debug(s"WS Client: received binary frame: $message")
      message.foreach(m => state.offerToQueue(m))
      ()
    }
    override def onPingFrame(payload: Array[Byte]): Unit = {
      izLogger.debug(s"WS Client: received ping frame. Responding with pong...")
      state.socket.get().sendPongFrame()
      ()
    }
  }
}

object WsInfraConnector {

  private final case class SocketListenerState[F[+_, +_]](
    requests: RefM2[F, Map[UUID, Queue[F[Throwable, _], WsWireResponse]]],
    socket: AtomicReference[WebSocket],
    finished: AtomicBoolean,
  ) {
    private def closeSocket(): Unit = {
      finished.set(true)
      socket.get().sendCloseFrame()
      ()
    }
    private def closeRequests(implicit UR: UnsafeRun2[F], M: Monad2[F]): Unit = {
      UR.unsafeRunAsync(
        requests.update_ {
          F.traverse_(_) {
            case (uid, queue) =>
              queue.offer(WsWireResponse.ResponseClose(uid, 0))
          }.as(Map.empty)
        }
      )(_ => ())
    }
    def closeAll(implicit UR: UnsafeRun2[F], M: Monad2[F]): Unit = {
      closeSocket()
      closeRequests
    }
    def offerToQueue(s: WsWireResponse)(implicit UR: UnsafeRun2[F], M: Monad2[F]): Unit = {
      UR.unsafeRunAsync {
        requests.get.flatMap {
          requestsQueues =>
            F.traverse_(requestsQueues.get(s.requestId))(_.offer(s))
        }
      }(_ => ())
    }
  }

  final class Resource[F[+_, +_]: Async2: UnsafeRun2: Fork2: Temporal2: AsyncThrowable: Primitives2](
    logger: LogIO2[F],
    izLogger: IzLogger,
    secretConf: HttpSecretConfig,
    entropy2: Entropy2[F],
  ) extends Lifecycle.Of[F[Throwable, _], WsInfraConnector[F]](for {
      client <- Lifecycle.fromAutoCloseable(F.syncThrowable {
        import zio.durationInt
        new DefaultAsyncHttpClient(
          new DefaultAsyncHttpClientConfig.Builder()
            .setWebSocketMaxBufferSize(64 * 1024 * 1024 * 8) // increase buffer size for 64MB, 128000000 - is default value
            .setWebSocketMaxFrameSize(64 * 1024 * 1024 * 8) // increase frame size for 64MB
            .setKeepAlive(true)
            .setSoKeepAlive(true)
            .setRequestTimeout(30.seconds) // 60 seconds is default
            .setPooledConnectionIdleTimeout(60.seconds) // 60 seconds is default
            .setConnectTimeout(30.seconds) // 5 seconds is default
            .setReadTimeout(60.seconds) // 60 seconds is default
            .setShutdownTimeout(15.seconds) // 15 seconds is default
            .build()
        )
      })
    } yield new WsInfraConnector[F](client, logger, izLogger, secretConf, entropy2))
}
