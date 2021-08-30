package d4s.codecs

import scala.language.experimental.macros

/** Describes a `ProjectionExpression` required for case class `T` */
final case class AttributeNames[T](attributeNames: Set[String]) {
  def projectionExpression: String                           = attributeNames.mkString("", ", ", "")
  def combine[A](that: AttributeNames[?]): AttributeNames[A] = AttributeNames(this.attributeNames ++ that.attributeNames)
  def ++[A](that: AttributeNames[?]): AttributeNames[A]      = AttributeNames(this.attributeNames ++ that.attributeNames)
}

object AttributeNames extends AttributeNamesInstances {
  def apply[T: AttributeNames]: AttributeNames[T] = implicitly

  def derived[T]: AttributeNames[T] = macro AttributeNamesMacro.attributeNamesMacro[T]

  def derivedNoError[T]: AttributeNames[T] = macro AttributeNamesMacro.attributeNamesNoCheckMacro[T]

  implicit def literalTupleAttributeNames[K <: String: ValueOf, V]: AttributeNames[(K, V)] = {
    AttributeNames(Set[String](valueOf))
  }
}

trait AttributeNamesInstances {
  implicit def autoMacroUtilsAttributeNames[T]: AttributeNames[T] = macro AttributeNamesMacro.attributeNamesMacro[T]
}
