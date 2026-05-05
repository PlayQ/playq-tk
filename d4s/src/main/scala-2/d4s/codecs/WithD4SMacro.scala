package d4s.codecs

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object WithD4SMacro {
  object MaterializeDerivationMacros {

    def materializeCodec[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[D4SDerivedCodecAsObject[A]] = c.Expr[D4SDerivedCodecAsObject[A]] {
      import c.universe.*
      q"new ${weakTypeOf[D4SDerivedCodecAsObject[A]]}(_root_.d4s.codecs.D4SEncoder.AsObject.derived[${weakTypeOf[A]}], _root_.d4s.codecs.D4SDecoder.derived[${weakTypeOf[A]}])"
    }

    def materializeAttributeCodec[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[D4SDerivedCodec[A]] = c.Expr[D4SDerivedCodec[A]] {
      import c.universe.*
      q"new ${weakTypeOf[D4SDerivedCodec[A]]}(_root_.d4s.codecs.D4SEncoder.derived[${weakTypeOf[A]}], _root_.d4s.codecs.D4SDecoder.derived[${weakTypeOf[A]}])"
    }

  }

  final case class D4SDerivedCodecAsObject[A](enc: D4SEncoder.AsObject[A], dec: D4SDecoder[A])
  object D4SDerivedCodecAsObject {
    implicit def materialize[A]: D4SDerivedCodecAsObject[A] = macro MaterializeDerivationMacros.materializeCodec[A]
  }

  final case class D4SDerivedCodec[A](enc: D4SEncoder[A], dec: D4SDecoder[A])
  object D4SDerivedCodec {
    implicit def materialize[A]: D4SDerivedCodec[A] = macro MaterializeDerivationMacros.materializeAttributeCodec[A]
  }
}
