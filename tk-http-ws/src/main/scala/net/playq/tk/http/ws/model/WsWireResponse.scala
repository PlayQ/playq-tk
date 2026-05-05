package net.playq.tk.http.ws.model

import com.google.protobuf.ByteString
import izumi.fundamentals.platform.exceptions.IzThrowable.*
import net.playq.tk.http.ws.model.WsWireResponse.{ResponseClose, ResponseErrored, ResponseMessage}
import net.playq.tk.http.ws.proto.{WsProtoResponse, WsProtoResponseClose, WsProtoResponseError, WsProtoResponseMessage}

import java.util.UUID

// response message is an ADT of message and terminal symbol to finish request stream
sealed trait WsWireResponse {
  def requestId: UUID

  final def toBytes: Array[Byte] = {
    (this match {
      case ResponseMessage(requestId, bytes) =>
        val message = WsProtoResponseMessage.newBuilder().setRequestId(requestId.toString).setMessage(ByteString.copyFrom(bytes))
        WsProtoResponse.newBuilder().setMessage(message).build()
      case ResponseClose(requestId, messagesSent) =>
        val message = WsProtoResponseClose.newBuilder().setRequestId(requestId.toString).setMessagesSent(messagesSent)
        WsProtoResponse.newBuilder().setClose(message).build()
      case ResponseErrored(requestId, error, trace) =>
        val message = WsProtoResponseError.newBuilder().setRequestId(requestId.toString).setError(error).setTrace(trace)
        WsProtoResponse.newBuilder().setError(message).build()
    }).toByteArray
  }
}
object WsWireResponse {
  final case class ResponseMessage(requestId: UUID, message: Array[Byte]) extends WsWireResponse
  final case class ResponseClose(requestId: UUID, messagesSent: Int) extends WsWireResponse
  final case class ResponseErrored(requestId: UUID, error: String, trace: String) extends WsWireResponse
  object ResponseErrored {
    def apply(requestId: UUID, err: Throwable): ResponseErrored = ResponseErrored(requestId, err.getMessage, err.stacktraceString)
  }

  def fromBytes(req: Array[Byte]): WsWireResponse = {
    val parsed = WsProtoResponse.parseFrom(req)
    if (parsed.hasMessage) {
      val m = parsed.getMessage
      ResponseMessage(UUID.fromString(m.getRequestId), m.getMessage.toByteArray)
    } else if (parsed.hasClose) {
      ResponseClose(UUID.fromString(parsed.getClose.getRequestId), parsed.getClose.getMessagesSent)
    } else {
      val e = parsed.getError
      ResponseErrored(UUID.fromString(e.getRequestId), e.getError, e.getTrace)
    }
  }
}
