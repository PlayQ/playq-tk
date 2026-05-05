package d4s.conditions

import d4s.codecs.{D4SAttributeKeyType, D4SEncoder}
import d4s.conditions.ConditionFieldSyntax.*
import d4s.table.DynamoField

import scala.language.implicitConversions

trait ConditionFieldSyntax {
  @inline implicit final def stringBasedFieldOps(s: String): StringBasedFieldOps      = new StringBasedFieldOps(s)
  @inline implicit final def pathBasedFieldOps(path: List[String]): PathBasedFieldOps = new PathBasedFieldOps(path)
}

object ConditionFieldSyntax {
  final class StringBasedFieldOps(private val name: String) extends AnyVal {
    def of[T]: TypedPathFieldOps[T] = new TypedPathFieldOps[T](List(name))

    def field[T: D4SAttributeKeyType: D4SEncoder]: DynamoField[T] = DynamoField(name)

    def existsField: Condition.attribute_exists                 = Condition.attribute_exists(List(name))
    def notExists: Condition.attribute_not_exists               = Condition.attribute_not_exists(List(name))
    def hasType(tpe: String): Condition.attribute_type          = Condition.attribute_type(List(name), tpe)
    def sizeField: Condition.size                               = Condition.size(List(name))
    infix def beginsWith(substr: String): Condition.begins_with = Condition.begins_with(List(name), substr)
    def isNull: Condition.attribute_is_null                     = Condition.attribute_is_null(List(name))
    def notNull: Condition.not                                  = Condition.not(isNull)
  }

  final class PathBasedFieldOps(private val path: List[String]) extends AnyVal {
    def of[T]: TypedPathFieldOps[T] = new TypedPathFieldOps[T](path)

    def existsField: Condition.attribute_exists                 = Condition.attribute_exists(path)
    def notExists: Condition.attribute_not_exists               = Condition.attribute_not_exists(path)
    def hasType(tpe: String): Condition.attribute_type          = Condition.attribute_type(path, tpe)
    def sizeField: Condition.size                               = Condition.size(path)
    infix def beginsWith(substr: String): Condition.begins_with = Condition.begins_with(path, substr)
    def isNull: Condition.attribute_is_null                     = Condition.attribute_is_null(path)
    def notNull: Condition.not                                  = Condition.not(isNull)
  }

  final class TypedPathFieldOps[+T](private val path: List[String]) extends AnyVal {
    def exists: Condition.attribute_exists                      = Condition.attribute_exists(path)
    def notExists: Condition.attribute_not_exists               = Condition.attribute_not_exists(path)
    def hasType(tpe: String): Condition.attribute_type          = Condition.attribute_type(path, tpe)
    def size: Condition.size                                    = Condition.size(path)
    infix def beginsWith(substr: String): Condition.begins_with = Condition.begins_with(path, substr)
    def isNull: Condition.attribute_is_null                     = Condition.attribute_is_null(path)
    def notNull: Condition.not                                  = Condition.not(isNull)

    infix def isIn[T0 >: T](set: Set[T0])(implicit enc: D4SEncoder[T0]): Condition.in[T0] = {
      Condition.in(path, set)
    }

    def between[T0 >: T](left: T0, right: T0)(implicit enc: D4SEncoder[T0]): Condition.between[T0] = {
      Condition.between(path, left, right)
    }

    def >[T0 >: T](threshold: T0)(implicit enc: D4SEncoder[T0]): Condition.logical[T0] = {
      Condition.logical(path, LogicalOperator.>, threshold)
    }

    def <[T0 >: T](threshold: T0)(implicit enc: D4SEncoder[T0]): Condition.logical[T0] = {
      Condition.logical(path, LogicalOperator.<, threshold)
    }

    def >=[T0 >: T](threshold: T0)(implicit enc: D4SEncoder[T0]): Condition.logical[T0] = {
      Condition.logical(path, LogicalOperator.>=, threshold)
    }

    def <=[T0 >: T](threshold: T0)(implicit enc: D4SEncoder[T0]): Condition.logical[T0] = {
      Condition.logical(path, LogicalOperator.<=, threshold)
    }

    @SuppressWarnings(Array("AvoidOperatorOverload"))
    def ===[T0 >: T](threshold: T0)(implicit enc: D4SEncoder[T0]): Condition.logical[T0] = {
      Condition.logical(path, LogicalOperator.==, threshold)
    }

    def <>[T0 >: T](threshold: T0)(implicit enc: D4SEncoder[T0]): Condition.logical[T0] = {
      Condition.logical(path, LogicalOperator.<>, threshold)
    }
  }

  object TypedPathFieldOps {
    @inline implicit final def toUncheckedOps[T](t: TypedPathFieldOps[T]): UncheckedTypedFieldOps[T]                          = new UncheckedTypedFieldOps(t.path)
    @inline implicit final def toIterableOps[T, C[t] <: Iterable[t]](t: TypedPathFieldOps[C[T]]): IterableTypedFieldOps[T, C] = new IterableTypedFieldOps(t.path)
  }

  final class IterableTypedFieldOps[T, C[t] <: Iterable[t]](private val field: List[String]) extends AnyVal {
    infix def contains(value: T)(implicit enc: D4SEncoder[T]): Condition.contains[T] = {
      Condition.contains(field, value)
    }
  }

  final class UncheckedTypedFieldOps[T](private val field: List[String]) extends AnyVal {
    infix def contains[T0](value: T0)(implicit enc: D4SEncoder[T0]): Condition.contains[T0] = {
      Condition.contains(field, value)
    }
  }
}
