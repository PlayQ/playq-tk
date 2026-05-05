package d4s.query.models

import d4s.table.DynamoField

trait WithProjectionExpression[+D] {
  def withProjectionExpression(f: Option[String] => Option[String]): D

  final def withProjectionExpression(expr: String): D = {
    withProjectionExpression(p => Some(p.fold("")(existingExpr => s"$existingExpr, ") + expr))
  }
  final def withProjectionExpression(fields: DynamoField[?]*): D = {
    withProjectionExpression(fields.mkString("", ", ", ""))
  }
}
