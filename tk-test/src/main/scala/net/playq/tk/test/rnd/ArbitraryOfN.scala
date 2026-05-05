package net.playq.tk.test.rnd

import org.scalacheck.util.Buildable
import org.scalacheck.{Arbitrary, Gen}

trait ArbitraryOfN[T] {
  def genOfN(n: Int): Gen[T]
}

object ArbitraryOfN extends ArbitraryOfNLowPriorityInstances {
  @inline def apply[T: ArbitraryOfN]: ArbitraryOfN[T] = implicitly

  def arbitraryOfN[T: ArbitraryOfN](size: Int): Arbitrary[T] = Arbitrary(ArbitraryOfN[T].genOfN(size))

  implicit def arbOfNStr(implicit arbChar: Arbitrary[Char]): ArbitraryOfN[String] = Gen.stringOfN(_, arbChar.arbitrary)
}

sealed trait ArbitraryOfNLowPriorityInstances {
  /** [[Arbitrary.arbContainer]] */
  implicit final def arbOfNContainer[C[_], T](
    implicit
    a: Arbitrary[T],
    b: Buildable[T, C[T]],
    t: C[T] => Iterable[T],
  ): ArbitraryOfN[C[T]] = Gen.buildableOfN[C[T], T](_, Arbitrary.arbitrary[T])

  /** [[Arbitrary.arbContainer2]] */
  implicit final def arbOfNContainer2[C[_, _], T, U](
    implicit
    a: Arbitrary[(T, U)],
    b: Buildable[(T, U), C[T, U]],
    t: C[T, U] => Iterable[(T, U)],
  ): ArbitraryOfN[C[T, U]] = Gen.buildableOfN[C[T, U], (T, U)](_, Arbitrary.arbitrary[(T, U)])
}
