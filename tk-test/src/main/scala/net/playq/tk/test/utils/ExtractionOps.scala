package net.playq.tk.test.utils

import org.scalactic.source.Position
import org.scalatest.*

import scala.reflect.{ClassTag, classTag}

trait ExtractionOps {
  this: Assertions =>

  implicit final class AnyExt[T](a: T) {
    def cast[T1 <: T: ClassTag](implicit pos: Position): T1 = {
      a match {
        case t1: T1 =>
          t1
        case o =>
          fail(s"$o expected to be ${classTag[T1]} but it isn't")
      }
    }
  }
}
