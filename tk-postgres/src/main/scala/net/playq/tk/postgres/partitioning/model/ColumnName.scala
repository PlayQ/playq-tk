package net.playq.tk.postgres.partitioning.model
import net.playq.tk.postgres.RawSQL

final case class ColumnName(column: String) extends RawSQL {
  override def sqlString: String = quoted(column)
}
