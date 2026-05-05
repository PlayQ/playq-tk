package d4s.query.models

import d4s.conditions.Condition

trait WithCondition[+D] {
  def withCondition(f: Condition => Condition): D

  final def withCondition(expr: Condition): D = withCondition((_: Condition) && expr)
}
