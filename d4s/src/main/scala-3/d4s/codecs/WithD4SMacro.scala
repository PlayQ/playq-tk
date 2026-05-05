package d4s.codecs

import scala.deriving.Mirror

object WithD4SMacro {

  final case class D4SDerivedCodecAsObject[A](enc: D4SEncoder.AsObject[A], dec: D4SDecoder[A])
  object D4SDerivedCodecAsObject {
    inline implicit def materialize[A: Mirror.Of]: D4SDerivedCodecAsObject[A] = {
      new D4SDerivedCodecAsObject(D4SEncoder.AsObject.derived[A], D4SDecoder.derived[A])
    }
  }

  final case class D4SDerivedCodec[A](enc: D4SEncoder[A], dec: D4SDecoder[A])
  object D4SDerivedCodec {
    inline implicit def materialize[A: Mirror.Of]: D4SDerivedCodec[A] = {
      new D4SDerivedCodec(D4SEncoder.derived[A], D4SDecoder.derived[A])
    }
  }
}
