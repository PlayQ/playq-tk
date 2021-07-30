package net.playq.tk.postgres.partitioning.model

import net.playq.tk.postgres.RawSQL

final case class PartitionName(schema: String, tablePartition: String) extends RawSQL {
  override def sqlString: String = s"${quoted(schema)}.${quoted(tablePartition)}"
}
