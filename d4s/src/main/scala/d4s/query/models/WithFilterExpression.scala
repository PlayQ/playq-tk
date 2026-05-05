package d4s.query.models

import d4s.conditions.Condition
import d4s.implicits.*

import java.time.ZonedDateTime

trait WithFilterExpression[+D] extends WithTableReference.Generic[D] {
  self: D =>

  def withFilterExpression(f: Condition => Condition): D

  final def withFilterExpression(expr: Condition): D = withFilterExpression((_: Condition) && expr)

  final def filterTtl(now: ZonedDateTime): D = {
    filterTtl(now.toEpochSecond)
  }

  final def filterTtl(nowEpochSeconds: Long): D = {
    tableRef.ttlField.fold[D](self) {
      ttlField =>
        self.withFilterExpression(ttlField.of[Long] >= nowEpochSeconds || ttlField.notExists)
    }
  }
}
