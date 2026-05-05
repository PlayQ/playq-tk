package d4s.codecs

import scala.quoted.{Expr, Quotes, Type, Varargs}

object ProjectionExpressionMacro {

  def attributeNamesMacro[T: Type](using qctx: Quotes): Expr[ProjectionExpression[T]] = {
    '{
      new ProjectionExpression[T](${ ProjectionExpressionMacro.fetchValsMacro[T](checkReserved = true) })
    }
  }

  def attributeNamesNoCheckMacro[T: Type](using qctx: Quotes): Expr[ProjectionExpression[T]] = {
    '{
      new ProjectionExpression[T](${ ProjectionExpressionMacro.fetchValsMacro[T](checkReserved = false) })
    }
  }

  def fetchValsMacro[T: Type](checkReserved: Boolean)(using quotes: Quotes): Expr[Set[String]] = {
    import quotes.reflect.*
    val typeOfT = TypeRepr.of[T]
    val members = typeOfT.typeSymbol.declarations.filter(_.isValDef).map(_.name.trim)

    if (checkReserved) {
      members.find(ProjectionExpression.ReservedWords.reservedWords contains _.toUpperCase).fold(()) {
        bannedWord =>
          report.errorAndAbort(
            s"""Field name `$bannedWord` is reserved in Dynamo! Please rename.
               |When checking attributes for Item type ${typeOfT.show}""".stripMargin,
            Position.ofMacroExpansion,
          )
      }
    }

    '{ Set(${ Varargs(members.map(Expr(_))) }*) }
  }

}
