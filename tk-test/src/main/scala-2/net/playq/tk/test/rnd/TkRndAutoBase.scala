package net.playq.tk.test.rnd

import magnolia1.Magnolia
import org.scalacheck.Arbitrary

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import net.playq.tk.test.rnd.TkRndAuto.DerivedArbitrary

trait TkRndAutoBase {
  implicit def genArbitrary[T]: Arbitrary[T] = macro Magnolia.gen[T]

  implicit def genDerivedArbitrary[T]: DerivedArbitrary[T] = macro TkRndAutoBase.materializeDerivedArbitrary[T]
}

object TkRndAutoBase {
  /**
    * Repeat the trick in [[pureconfig.module.magnolia.ExportedMagnolia]]
    * to avoid splicing a tree that points to another macro and instead
    * execute everything in one step.
    *
    * Since [[TkRndAutoBase.genDerivedArbitrary]] is directly inside [[TkRndAutoBase]],
    * call to Magnolia.gen will find the correct join/split methods
    * when it tries to find them in its `c.prefix`
    */
  def materializeDerivedArbitrary[T: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
    import c.universe.*
    val instance = Magnolia.gen[T](c)
    q"new ${weakTypeOf[DerivedArbitrary[T]]}($instance)"
  }
}
