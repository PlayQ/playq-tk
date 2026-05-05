package d4s.codecs

import scala.reflect.macros.blackbox

object ProjectionExpressionMacro {

  def attributeNamesMacro[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ProjectionExpression[T]] = {
    c.universe.reify {
      new ProjectionExpression[T](ProjectionExpressionMacro.fetchValsMacro[T](c)(checkReserved = true).splice)
    }
  }

  def attributeNamesNoCheckMacro[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ProjectionExpression[T]] = {
    c.universe.reify {
      new ProjectionExpression[T](ProjectionExpressionMacro.fetchValsMacro[T](c)(checkReserved = false).splice)
    }
  }

  def fetchValsMacro[T: c.WeakTypeTag](c: blackbox.Context)(checkReserved: Boolean): c.Expr[Set[String]] = {
    import c.universe.*

    val members = c.weakTypeOf[T].members.toList.collect {
      case m: TermSymbolApi if m.isVal => m.name.decodedName.toString.trim
    }

    if (checkReserved) {
      members.find(ProjectionExpression.ReservedWords.reservedWords contains _.toUpperCase).fold(()) {
        bannedWord =>
          c.error(
            c.enclosingPosition,
            s"""Field name `$bannedWord` is reserved in Dynamo! Please rename.
               |When checking attributes for Item type ${weakTypeOf[T]}""".stripMargin,
          )
      }
    }

    reify {
      c.Expr[Set[String]](q"$members.toSet").splice
    }
  }

}
