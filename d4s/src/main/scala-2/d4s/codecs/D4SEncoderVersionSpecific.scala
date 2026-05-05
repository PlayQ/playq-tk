package d4s.codecs

import magnolia1.Magnolia

import scala.language.experimental.macros

trait D4SEncoderVersionSpecific {
  final def derived[T]: D4SEncoder[T] = macro Magnolia.gen[T]
}
