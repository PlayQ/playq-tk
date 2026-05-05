package d4s.codecs

import net.playq.tk.util.magnoliacompat.MagnoliaCompat.{CaseClass, SealedTrait}
import magnolia1.Derivation

import scala.deriving.Mirror

trait D4SEncoderAsObjectVersionSpecific { self =>
  def join[T](caseClass: CaseClass[D4SEncoder, T]): D4SEncoder[T]
  def split[T](sealedTrait: SealedTrait[D4SEncoder, T]): D4SEncoder[T]

  inline final def derived[A](using Mirror.Of[A]): D4SEncoder.AsObject[A] = __DerivationImpl.derived[A].asInstanceOf[D4SEncoder.AsObject[A]]

  object __DerivationImpl extends Derivation[D4SEncoder] {
    export self.{join, split}
  }
}
