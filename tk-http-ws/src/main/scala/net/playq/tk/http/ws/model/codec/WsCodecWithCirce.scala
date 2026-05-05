package net.playq.tk.http.ws.model.codec

import io.circe.Codec
import izumi.idealingua.runtime.circe.DerivationDerivedCodec

abstract class WsCodecWithCirce[A]()(implicit derivedCodec: => DerivationDerivedCodec[A]) {
  def this(proxy: WsCodecWithCirce[A]) = this()(using DerivationDerivedCodec(proxy.circeCodec))

  implicit lazy val circeCodec: Codec.AsObject[A] = derivedCodec.value
  implicit lazy val wsCodec: WsWireCodec[A]       = WsWireCodec.fromCirce[A](using circeCodec, circeCodec)
}
