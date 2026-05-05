package net.playq.tk.test.utils

import org.scalactic.source.Position
import org.scalatest.*

import scala.annotation.unused
import scala.reflect.{classTag, ClassTag}

trait ExtractionOps {
  this: Assertions =>

  implicit final class AnyExt[T](a: T) {
    def cast[T1 <: T: ClassTag](implicit @unused pos: Position): T1 = {
      a match {
        case t1: T1 =>
          t1
        case o =>
          fail(s"$o expected to be ${classTag[T1]} but it isn't")
      }
    }
  }
}
