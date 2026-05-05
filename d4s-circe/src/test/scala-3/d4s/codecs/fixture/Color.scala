package d4s.codecs.fixture

import d4s.codecs.D4SCodec
import d4s.codecs.circe.D4SCirceCodec
import io.circe.derivation.{ConfiguredEnumDecoder, ConfiguredEnumEncoder}
import io.circe.{Decoder, Encoder}

sealed trait Color
object Color {
  val circeBasedCodec: D4SCodec[Color] = {
    implicit val enc: Encoder[Color] = ConfiguredEnumEncoder.derive[Color]()
    implicit val dec: Decoder[Color] = ConfiguredEnumDecoder.derive[Color]()
    D4SCirceCodec.derived[Color]
  }
}

case object Red extends Color
case object Green extends Color
case object Blue extends Color
