package net.playq.tk.http.ws.model.codec

import io.circe.Encoder
import io.circe.syntax.EncoderOps
import net.playq.tk.util.data.convert.DataEncoder

import java.nio.charset.StandardCharsets

trait WsWireEncoder[T] extends DataEncoder[T, Array[Byte]]

object WsWireEncoder {
  def apply[T](implicit ev: WsWireEncoder[T]): WsWireEncoder[T]   = ev
  def encode[T](t: T)(implicit ev: WsWireEncoder[T]): Array[Byte] = ev.encode(t)

  def from[T](toBytes: T => Array[Byte]): WsWireEncoder[T] = new WsWireEncoder[T] {
    override def encode(from: T): Array[Byte] = toBytes(from)
  }
  def fromCirce[T: Encoder]: WsWireEncoder[T] = new WsWireEncoder[T] {
    override def encode(from: T): Array[Byte] = from.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  implicit val unitEncoder: WsWireEncoder[Unit]         = WsWireEncoder.from[Unit](_ => Array.empty)
  implicit val stringEncoder: WsWireEncoder[String]     = WsWireEncoder.from[String](_.getBytes(StandardCharsets.UTF_8))
  implicit val boolEncoder: WsWireEncoder[Boolean]      = WsWireEncoder.from[Boolean](b => if (b) Array(1.toByte) else Array(0.toByte))
  implicit val bytesEncoder: WsWireEncoder[Array[Byte]] = WsWireEncoder.from[Array[Byte]](identity)
}
