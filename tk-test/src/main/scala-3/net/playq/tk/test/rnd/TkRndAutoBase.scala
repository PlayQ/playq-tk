package net.playq.tk.test.rnd

import magnolia1.Derivation
import org.scalacheck.Arbitrary

import scala.deriving.Mirror

import net.playq.tk.test.rnd.TkRndAuto.DerivedArbitrary

trait TkRndAutoBase extends Derivation[Arbitrary] {

  inline implicit def genArbitrary[T](using Mirror.Of[T]): Arbitrary[T] = derived[T]

  inline implicit def genDerivedArbitrary[T](using Mirror.Of[T]): DerivedArbitrary[T] = DerivedArbitrary(derived[T])

}
