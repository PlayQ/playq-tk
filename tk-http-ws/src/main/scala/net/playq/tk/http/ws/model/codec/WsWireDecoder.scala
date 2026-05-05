package net.playq.tk.http.ws.model.codec

import io.circe.Decoder
import net.playq.tk.util.data.convert.DataDecoder

import java.nio.charset.StandardCharsets

trait WsWireDecoder[T] extends DataDecoder[Array[Byte], T]

object WsWireDecoder {
  def apply[T](implicit ev: WsWireDecoder[T]): WsWireDecoder[T]     = ev
  def decode[T](arr: Array[Byte])(implicit ev: WsWireDecoder[T]): T = ev.decode(arr)

  def from[T](fromBytes: Array[Byte] => T): WsWireDecoder[T] = new WsWireDecoder[T] {
    override def decode(from: Array[Byte]): T = fromBytes(from)
  }
  def fromCirce[T: Decoder]: WsWireDecoder[T] = new WsWireDecoder[T] {
    override def decode(from: Array[Byte]): T = {
      io.circe.parser.decode[T](new String(from, StandardCharsets.UTF_8)) match {
        case Left(value)  => throw value
        case Right(value) => value
      }
    }
  }

  implicit val unitDecoder: WsWireDecoder[Unit]         = WsWireDecoder.from[Unit](_ => ())
  implicit val stringDecoder: WsWireDecoder[String]     = WsWireDecoder.from[String](new String(_, StandardCharsets.UTF_8))
  implicit val boolDecoder: WsWireDecoder[Boolean]      = WsWireDecoder.from[Boolean](_.headOption.exists(_ > 0.toByte))
  implicit val bytesDecoder: WsWireDecoder[Array[Byte]] = WsWireDecoder.from[Array[Byte]](identity)
}
