package net.playq.tk.http.ws

import cats.effect.std.Queue
import fs2.Stream
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Entropy2, Error2, F, Fork2, IO2, RefM2, Temporal2}
import izumi.reflect.Tag
import logstage.IzLogger
import net.playq.tk.http.ws.WsInfraException.ThrowableWsOps
import net.playq.tk.http.ws.model.WsWireRequest.RequestMessage
import net.playq.tk.http.ws.model.codec.{WsWireDecoder, WsWireEncoder}
import net.playq.tk.http.ws.model.{WsWireRequest, WsWireResponse}
import net.playq.tk.quantified.{AsyncThrowable, ConcurrentThrowable}
import org.asynchttpclient.netty.ws.NettyWebSocket

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.{DurationInt, FiniteDuration}

abstract class WsInfraConnection[F[+_, +_]: Error2: Temporal2: ConcurrentThrowable] {
  def isOpen: Boolean

  def subscribeGeneric[Req: WsWireEncoder, Rsp: WsWireDecoder](tag: String, req: Req): Stream[F[Throwable, _], Rsp]
  def subscribe[Req: WsWireEncoder: Tag, Rsp: WsWireDecoder](req: Req): Stream[F[Throwable, _], Rsp]

  final def exchangeGeneric[Req: WsWireEncoder, Rsp: WsWireDecoder](tag: String, req: Req, timeout: FiniteDuration = 5.minutes): F[Throwable, Rsp] = {
    subscribeGeneric(tag, req).head.compile.last.timeout(timeout).map(_.flatten).fromOption(WsInfraException.ExchangeFailed)
  }
  final def exchange[Req: WsWireEncoder: Tag, Rsp: WsWireDecoder](req: Req, timeout: FiniteDuration = 5.minutes): F[Throwable, Rsp] = {
    subscribe(req).head.compile.last.timeout(timeout).map(_.flatten).fromOption(WsInfraException.ExchangeFailed)
  }
}

object WsInfraConnection {
  def nettyResource[F[+_, +_]: IO2: Fork2: Temporal2: AsyncThrowable](
    nettyWebSocket: NettyWebSocket,
    requestsQueues: RefM2[F, Map[UUID, Queue[F[Throwable, _], WsWireResponse]]],
    logger: IzLogger,
    entropy2: Entropy2[F],
  ): Lifecycle[F[Throwable, _], Netty[F]] = Lifecycle.make {
    F.sync {
      new Netty(nettyWebSocket, requestsQueues, logger, entropy2)
    }
  } {
    connection =>
      for {
        // close socket connection
        _ <- connection.sendCloseFrame().orTerminate
        // close requests queues
        _ <- requestsQueues.update_ {
          F.traverse(_) {
            case (id, queue) =>
              queue.offer(WsWireResponse.ResponseErrored(id, new RuntimeException("WS connection closed."))).orTerminate
          }.as(Map.empty)
        }
      } yield ()
  }

  final class Netty[F[+_, +_]: IO2: Fork2: Temporal2: AsyncThrowable](
    nettyWebSocket: NettyWebSocket,
    requestsQueues: RefM2[F, Map[UUID, Queue[F[Throwable, _], WsWireResponse]]],
    logger: IzLogger,
    entropy2: Entropy2[F],
  ) extends WsInfraConnection[F] {
    private val MaxCloseAttempts = 10
    private val CloseDelaySleep  = 300.millis

    def subscribeGeneric[Req: WsWireEncoder, Rsp: WsWireDecoder](tag: String, req: Req): Stream[F[Throwable, _], Rsp] = {
      for {
        requestId    <- Stream.eval(entropy2.nextTimeUUID())
        reqEncoded   <- Stream.eval(F.syncThrowable(WsWireEncoder.encode(req)).leftMapCodecFailure)
        messageBytes <- Stream.eval(F.syncThrowable(RequestMessage(requestId, tag, reqEncoded).toBytes).leftMapCodecFailure)
        _            <- Stream.eval(sendBinaryFrame(messageBytes))
        queue        <- Stream.eval {
          requestsQueues.modify {
            oldQueues =>
              Queue.unbounded[F[Throwable, _], WsWireResponse].map {
                queue =>
                  (queue, oldQueues ++ Map(requestId -> queue))
              }
          }
        }
        messagesReceived = new AtomicInteger(0)
        closeAttempts    = new AtomicInteger(0)
        res             <- Stream
          .fromQueueUnterminated(queue)
          .evalMapFilter[F[Throwable, _], Option[WsWireResponse.ResponseMessage]] {
            case m: WsWireResponse.ResponseMessage =>
              F.sync(messagesReceived.incrementAndGet()).as(Some(Some(m)))
            case c: WsWireResponse.ResponseClose if c.messagesSent <= messagesReceived.get() =>
              F.pure(Some(None))
            case c: WsWireResponse.ResponseClose if closeAttempts.incrementAndGet() < MaxCloseAttempts =>
              F.suspendSafe {
                val missing      = c.messagesSent - messagesReceived.get()
                val attemptsLeft = MaxCloseAttempts - closeAttempts.get()
                logger.debug(s"WS Client: request close requested, but not all messages received, close will be delayed for $CloseDelaySleep: $missing, $attemptsLeft.")
                F.fork(F.sleep(CloseDelaySleep).guarantee(queue.offer(c).orTerminate)).as(None)
              }
            case c: WsWireResponse.ResponseClose =>
              F.suspendSafe {
                val missing = c.messagesSent - messagesReceived.get()
                logger.warn(s"WS Client: request close requested, but not all messages received: $missing messages after $MaxCloseAttempts.")
                F.fail(WsInfraException.RequestFailed(
                  s"Request: can not deliver all responses after $MaxCloseAttempts attempts. Missing ${c.messagesSent - messagesReceived.get()}."
                ))
              }
            case e: WsWireResponse.ResponseErrored =>
              F.fail(WsInfraException.RequestFailed(s"Request: ${e.requestId} failed with error - ${e.error}\n${e.trace}"))
          }
          .unNoneTerminate
          .evalMap(rsp => F.syncThrowable(WsWireDecoder.decode(rsp.message)).leftMapCodecFailure)
          .onFinalize {
            for {
              _            <- requestsQueues.update(old => F.sync(old.removed(requestId)))
              closeMessage <- F.syncThrowable(WsWireRequest.RequestClose(requestId).toBytes).leftMapCodecFailure
              _            <- sendBinaryFrame(closeMessage)
            } yield ()
          }
      } yield res
    }

    def subscribe[Req: WsWireEncoder: Tag, Rsp: WsWireDecoder](req: Req): Stream[F[Throwable, _], Rsp] = {
      subscribeGeneric(Tag[Req].toString, req)
    }

    def isOpen: Boolean = nettyWebSocket.isOpen

    def sendCloseFrame(): F[Throwable, Unit] = F.syncThrowable {
      logger.debug(s"WS Client: sending closing frame.")
      nettyWebSocket.sendCloseFrame()
      ()
    }

    def sendBinaryFrame(frame: Array[Byte]): F[Throwable, Unit] = F.syncThrowable {
      logger.debug(s"WS Client: sending binary frame.")
      nettyWebSocket.sendBinaryFrame(frame)
      ()
    }
  }
}
