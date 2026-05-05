package d4s.codecs

import magnolia1.Magnolia

import scala.language.experimental.macros

trait D4SEncoderAsObjectVersionSpecific {
  final def derived[A]: D4SEncoder.AsObject[A] = macro Magnolia.gen[A]
}
