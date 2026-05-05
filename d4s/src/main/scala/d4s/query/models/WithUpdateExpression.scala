package d4s.query.models

trait WithUpdateExpression[+D] {
  def withUpdateExpression(f: String => String): D

  final def withUpdateExpression(expr: String): D = withUpdateExpression {
    existingExpr => if (existingExpr.isEmpty) expr else s"$existingExpr $expr"
  }
}
