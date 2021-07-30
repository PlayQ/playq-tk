package net.playq.tk.postgres.partitioning.model

import net.playq.tk.postgres.RawSQL

final case class ColumnReference(table: TableName, column: String) extends RawSQL {
  override def sqlString: String = s"${table.sqlString}.${quoted(column)}"
}
