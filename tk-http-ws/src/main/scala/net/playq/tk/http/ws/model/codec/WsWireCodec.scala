package net.playq.tk.http.ws.model.codec

import io.circe.{Decoder, Encoder}

trait WsWireCodec[T] extends WsWireEncoder[T] with WsWireDecoder[T]

object WsWireCodec {
  def apply[T](implicit ev: WsWireDecoder[T]): WsWireDecoder[T] = ev

  def fromPair[T](enc: WsWireEncoder[T], dec: WsWireDecoder[T]): WsWireCodec[T] = new WsWireCodec[T] {
    override def encode(from: T): Array[Byte] = enc.encode(from)
    override def decode(from: Array[Byte]): T = dec.decode(from)
  }

  def from[T](toBytes: T => Array[Byte])(fromBytes: Array[Byte] => T): WsWireCodec[T] = new WsWireCodec[T] {
    override def encode(from: T): Array[Byte] = toBytes(from)
    override def decode(from: Array[Byte]): T = fromBytes(from)
  }

  def fromCirce[T: Encoder: Decoder]: WsWireCodec[T] = fromPair(WsWireEncoder.fromCirce, WsWireDecoder.fromCirce)
}
