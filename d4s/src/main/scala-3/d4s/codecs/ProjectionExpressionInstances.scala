package d4s.codecs

trait ProjectionExpressionInstances {
  inline def derived[T]: ProjectionExpression[T] = ${ ProjectionExpressionMacro.attributeNamesMacro[T] }

  inline def derivedNoError[T]: ProjectionExpression[T] = ${ ProjectionExpressionMacro.attributeNamesNoCheckMacro[T] }

  inline implicit def autoMacroUtilsAttributeNames[T]: ProjectionExpression[T] = ${ ProjectionExpressionMacro.attributeNamesMacro[T] }
}
