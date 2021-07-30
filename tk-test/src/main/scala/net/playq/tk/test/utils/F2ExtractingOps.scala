package net.playq.tk.test.utils

import izumi.functional.bio.{Exit, Functor2, Panic2}
import org.scalactic.source.Position
import org.scalatest.Assertions

import scala.reflect.ClassTag

trait F2ExtractingOps extends ExtractionOps {
  this: Assertions =>

  implicit final class F2RunOps[F[+_, +_]: Panic2, E, T](private val f: F[E, T])(implicit pos: Position) {

    def asBad[E1 <: E: ClassTag]: F[Nothing, E1] = {
      f.sandbox.redeemPure(
        {
          case Exit.Error(e: E1, _)          => e
          case Exit.Error(e, trace)          => fail(s"Expected a failure of class ${implicitly[ClassTag[E1]]}, but got a different failure: $e trace=$trace")
          case Exit.Termination(e, _, trace) => fail(s"Expected a failure of class ${implicitly[ClassTag[E1]]}, but got a defect: $e trace=$trace")
          case Exit.Interruption(e, trace)   => fail(s" Expected a failure of class ${implicitly[ClassTag[E1]]}, bug got an interruption: $e trace=$trace")
        },
        v => fail(s"Expected a failure of class ${implicitly[ClassTag[E1]]}, but got good branch with value=$v"),
      )
    }

    def asDead[E1 <: Throwable: ClassTag]: F[Nothing, E1] = {
      f.sandbox.redeemPure(
        {
          case Exit.Termination(compound, es, trace) =>
            es.collectFirst {
              case e: E1 => e
            }.getOrElse(fail(s"Expected a defect of class ${implicitly[ClassTag[E1]]}, but got a different defect: $compound trace=$trace"))
          case Exit.Error(v, trace)        => fail(s"Expected a defect of class ${implicitly[ClassTag[E1]]}, but got an error branch with value=$v trace=$trace")
          case Exit.Interruption(v, trace) => fail(s" Expected a defect of class ${implicitly[ClassTag[E1]]}, bug got interruption with value=$v trace=$trace")
        },
        v => fail(s"Expected a defect of class ${implicitly[ClassTag[E1]]}, but got good branch with value=$v"),
      )
    }
  }

  implicit final class F2CastingOps[F[+_, +_], E, T](private val f: F[E, T])(implicit pos: Position) {
    def fcast[T1 <: T: ClassTag](implicit F: Functor2[F]): F[E, T1] = {
      f.map(_.cast[T1])
    }
  }

}
