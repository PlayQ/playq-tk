package net.playq.tk.test.rnd

import magnolia1.{CaseClass, SealedTrait}
import org.scalacheck.tk.hack.GenHack.GenPrivate.*
import org.scalacheck.{Arbitrary, Gen}

object TkRndAuto extends TkRndAutoBase {

  // NOTE: this class must be defined in TkRndAuto object, so that the
  // `TkRndAutoBase#genDerivedArbitrary` implicit ends up in its implicit scope
  final case class DerivedArbitrary[T](arbitrary: Arbitrary[T]) extends AnyVal
  object DerivedArbitrary {
    @inline def apply[T: DerivedArbitrary](implicit d: DummyImplicit): DerivedArbitrary[T] = implicitly
  }

  type Typeclass[T] = Arbitrary[T]

  def join[T](caseClass: CaseClass[Typeclass, T]): Typeclass[T] = Arbitrary[T] {
    Gen.lzy {
      caseClass.parameters
        .foldRight[(Gen[List[Any]], Int)](Gen.const(Nil) -> 0) {
          case (param, (t, l)) =>
            field(param.typeclass.arbitrary, t, l) -> (l + 1)
        }._1.map(caseClass.rawConstruct(_))
    }
  }

  def split[T](sealedTrait: SealedTrait[Typeclass, T]): Typeclass[T] = Arbitrary[T] {
    failOnStackOverflow {
      Gen.lzy {
        sealedTrait.subtypes
          .foldRight[(Gen[T], Int)](Gen.fail -> 0) {
            case (sub, (t, l)) =>
              branch[T](sub.typeclass.arbitrary, t, l) -> (l + 1)
          }._1
      }
    }
  }

  /**
    * reason: allow generators for recursive sealed traits such as flower's `FType`/`FValue` to finish
    *
    * origin 1: https://github.com/alexarchambault/scalacheck-shapeless/blob/a1c12b645e592287e77513d4e0ecf45037cd8754/core/shared/src/main/scala/org/scalacheck/derive/MkArbitrary.scala#L33
    *           https://github.com/alexarchambault/scalacheck-shapeless/issues/50
    *
    * origin 2: https://github.com/alexarchambault/scalacheck-shapeless/blob/a1c12b645e592287e77513d4e0ecf45037cd8754/core/shared/src/main/scala/org/scalacheck/derive/GenExtra.scala#L16
    */
  def failOnStackOverflow[T](gen: Gen[T]): Gen[T] = {
    Gen._private_gen {
      (p, seed) =>
        try gen._private_doApply(p, seed)
        catch {
          // likely not fine on Scala JS
          case _: StackOverflowError =>
            Gen._private_r(None, seed.next)
        }
    }
  }

  /** origin: https://github.com/alexarchambault/scalacheck-shapeless/blob/a1c12b645e592287e77513d4e0ecf45037cd8754/core/shared/src/main/scala/org/scalacheck/derive/MkArbitrary.scala#L105 */
  private[this] def field(headArbitrary: Gen[Any], tailArbitrary: Gen[List[Any]], n: Int): Gen[List[Any]] = {
    Gen.sized {
      size0 =>
        if (size0 < 0)
          // unlike positive values, don't split negative sizes any further, and let subsequent Gen handle them
          for {
            head <- Gen.resize(size0, Gen.lzy(headArbitrary))
            tail <- Gen.resize(size0, Gen.lzy(tailArbitrary))
          } yield head :: tail
        else {
          // take a fraction of approximately 1 / (n + 1) from size for the head, leave the
          // remaining for the tail

          val size             = size0 max 0
          val remainder        = size % (n + 1)
          val fromRemainderGen =
            if (remainder > 0)
              Gen.choose(1, n).map(r => if (r <= remainder) 1 else 0)
            else
              Gen.const(0)

          for {
            fromRemainder <- fromRemainderGen
            headSize       = size / (n + 1) + fromRemainder
            head          <- Gen.resize(headSize, Gen.lzy(headArbitrary))
            tail          <- Gen.resize(size - headSize, Gen.lzy(tailArbitrary))
          } yield head :: tail
        }
    }
  }

  /** origin https://github.com/alexarchambault/scalacheck-shapeless/blob/a1c12b645e592287e77513d4e0ecf45037cd8754/core/shared/src/main/scala/org/scalacheck/derive/MkArbitrary.scala#L199 */
  private[this] def branch[T](headArbitrary: Gen[T], tailArbitrary: Gen[T], n: Int): Gen[T] = {
    Gen.sized {
      size =>
        /*
         * Unlike MkCoproductArbitrary above, try to generate a value no matter what (no Gen.fail).
         * This can blow the stack for recursive types, so should be avoided for those.
         */
        val nextSize = (size - 1) max 0
        Gen.frequency(
          1 -> Gen.resize(nextSize, Gen.lzy(headArbitrary)),
          n -> Gen.resize(nextSize, Gen.lzy(tailArbitrary)),
        )
    }
  }
}
