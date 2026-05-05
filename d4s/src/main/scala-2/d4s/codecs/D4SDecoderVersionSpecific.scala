package d4s.codecs

import magnolia1.Magnolia

import scala.language.experimental.macros

trait D4SDecoderVersionSpecific {
  final def derived[A]: D4SDecoder[A] = macro Magnolia.gen[A]
}
