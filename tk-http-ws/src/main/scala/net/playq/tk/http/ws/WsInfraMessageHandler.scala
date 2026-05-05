package net.playq.tk.http.ws

import cats.effect.kernel.Temporal
import fs2.Stream
import izumi.functional.bio.IO2
import izumi.reflect.Tag
import logstage.LogIO2
import net.playq.tk.http.ws.model.WsWireRequest.RequestMessage
import net.playq.tk.http.ws.model.WsWireResponse
import net.playq.tk.http.ws.model.codec.{WsWireDecoder, WsWireEncoder}

import java.util.concurrent.atomic.AtomicInteger

trait WsInfraMessageHandler[F[+_, +_], Req, Rsp] {
  def acceptable: String
  def handle(message: Req): Stream[F[Throwable, _], Rsp]
  def handleWire(message: RequestMessage)(implicit F: IO2[F], logger: LogIO2[F], TT: Temporal[F[Throwable, _]]): Stream[F[Throwable, _], WsWireResponse]
}

object WsInfraMessageHandler {

  abstract class Tagged[F[+_, +_], Req: WsWireDecoder: Tag, Rsp: WsWireEncoder] extends Generic[F, Req, Rsp] {
    def acceptable: String = Tag[Req].toString
  }

  abstract class Generic[F[+_, +_], Req: WsWireDecoder, Rsp: WsWireEncoder] extends WsInfraMessageHandler[F, Req, Rsp] {
    def acceptable: String

    def handle(message: Req): Stream[F[Throwable, _], Rsp]

    final def handleWire(message: RequestMessage)(implicit F: IO2[F], logger: LogIO2[F], TT: Temporal[F[Throwable, _]]): Stream[F[Throwable, _], WsWireResponse] = {
      val messagesSent   = new AtomicInteger(0)
      val responseStream = {
        for {
          req <- Stream.eval(F.syncThrowable(WsWireDecoder.decode(message.message)))
          _   <- Stream.eval(logger.debug(s"WS Server: processing message: $req"))
          rsp <- handle(req)
          enc <- Stream.eval(F.syncThrowable(WsWireEncoder.encode(rsp)))
          _   <- Stream.eval(F.sync(messagesSent.incrementAndGet()).void)
        } yield WsWireResponse.ResponseMessage(message.requestId, enc)
      }
      responseStream.append(Stream.eval(F.sync(WsWireResponse.ResponseClose(message.requestId, messagesSent.get()))))
    }
  }

  def from[F[+_, +_], Req: WsWireDecoder: Tag, Rsp: WsWireEncoder](func: Req => Stream[F[Throwable, _], Rsp]): WsInfraMessageHandler[F, Req, Rsp] = {
    new WsInfraMessageHandler.Tagged[F, Req, Rsp] {
      override def handle(message: Req): Stream[F[Throwable, _], Rsp] = func(message)
    }
  }
}
