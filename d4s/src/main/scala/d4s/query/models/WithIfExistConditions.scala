package d4s.query.models

import d4s.conditions.Condition
import d4s.conditions.Condition.{attribute_exists, attribute_not_exists}

@SuppressWarnings(Array("UnsafeTraversableMethods"))
trait WithIfExistConditions[+D <: WithCondition[D]] extends WithTableReference.Generic[D] {
  self: D =>

  final def ifExists(): D = {
    withCondition(tableRef.key.keyNames.map[Condition](s => attribute_exists(List(s))).reduceLeft(_ && _))
  }

  final def ifNotExists(): D = {
    withCondition(tableRef.key.keyNames.map[Condition](s => attribute_not_exists(List(s))).reduceLeft(_ && _))
  }
}
