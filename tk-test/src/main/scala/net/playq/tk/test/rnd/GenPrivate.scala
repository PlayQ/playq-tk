package org.scalacheck.tk.hack.GenHack

import org.scalacheck.Gen
import org.scalacheck.Gen.R
import org.scalacheck.rng.Seed

object GenPrivate {

  implicit final class GenOps[T](private val gen: Gen[T]) extends AnyVal {
    def _private_doApply(p: Gen.Parameters, seed: Seed): R[T] = {
      gen.doApply(p, seed)
    }
  }

  implicit final class GenPrivateOps(private val GenObj: Gen.type) extends AnyVal {
    def _private_gen[T](f: (Gen.Parameters, Seed) => R[T]): Gen[T] = {
      GenObj.gen(f)
    }

    def _private_r[T](r: Option[T], sd: Seed): R[T] = {
      GenObj.r(r, sd)
    }
  }

}
