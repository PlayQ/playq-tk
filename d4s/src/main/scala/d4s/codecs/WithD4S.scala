package d4s.codecs

import d4s.codecs.WithD4SMacro.D4SDerivedCodecAsObject

abstract class WithD4S[T]()(implicit derivedCodec: => D4SDerivedCodecAsObject[T]) {
  implicit lazy val codec: D4SCodec.AsObject[T] = D4SCodec.AsObject.fromPair(derivedCodec.enc, derivedCodec.dec)
}
