package d4s.codecs.circe

import d4s.codecs.D4SCodec
import io.circe.{Decoder, Encoder}

object D4SCirceCodec {
  def derived[T: Encoder: Decoder]: D4SCodec[T] = D4SCodec.fromPair(D4SCirceEncoder.derived, D4SCirceDecoder.derived)

  object AsObject {
    def derived[T: Encoder.AsObject: Decoder]: D4SCodec.AsObject[T] = D4SCodec.AsObject.fromPair(D4SCirceEncoder.AsObject.derived, D4SCirceDecoder.derived)
  }
}
