package net.playq.tk.http.ws.model

import com.google.protobuf.ByteString
import izumi.idealingua.runtime.circe.IRTWithCirce
import net.playq.tk.http.ws.model.WsWireRequest.{RequestClose, RequestMessage}
import net.playq.tk.http.ws.proto.{WsProtoRequest, WsProtoRequestClose, WsProtoRequestMessage}

import java.util.UUID

// request message contains request tag to find associated tag
sealed trait WsWireRequest {
  def requestId: UUID

  def toBytes: Array[Byte] = {
    (this match {
      case RequestMessage(requestId, tag, bytes) =>
        val message = WsProtoRequestMessage.newBuilder().setRequestId(requestId.toString).setTag(tag).setMessage(ByteString.copyFrom(bytes))
        WsProtoRequest.newBuilder().setMessage(message).build()
      case RequestClose(requestId) =>
        val message = WsProtoRequestClose.newBuilder().setRequestId(requestId.toString)
        WsProtoRequest.newBuilder().setClose(message).build()
    }).toByteArray
  }
}

object WsWireRequest {
  final case class RequestMessage(requestId: UUID, tag: String, message: Array[Byte]) extends WsWireRequest
  object RequestMessage extends IRTWithCirce[RequestMessage]

  final case class RequestClose(requestId: UUID) extends WsWireRequest
  object RequestClose extends IRTWithCirce[RequestClose]

  def fromBytes(req: Array[Byte]): WsWireRequest = {
    val parsed = WsProtoRequest.parseFrom(req)
    if (parsed.hasMessage) {
      val m = parsed.getMessage
      RequestMessage(UUID.fromString(m.getRequestId), m.getTag, m.getMessage.toByteArray)
    } else {
      RequestClose(UUID.fromString(parsed.getClose.getRequestId))
    }
  }
}
