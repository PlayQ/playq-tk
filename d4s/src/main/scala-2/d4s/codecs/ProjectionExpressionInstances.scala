package d4s.codecs

import scala.language.experimental.macros

trait ProjectionExpressionInstances {
  def derived[T]: ProjectionExpression[T] = macro ProjectionExpressionMacro.attributeNamesMacro[T]

  def derivedNoError[T]: ProjectionExpression[T] = macro ProjectionExpressionMacro.attributeNamesNoCheckMacro[T]

  implicit def autoMacroUtilsAttributeNames[T]: ProjectionExpression[T] = macro ProjectionExpressionMacro.attributeNamesMacro[T]
}
