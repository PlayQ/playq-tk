package net.playq.tk.postgres.partitioning.model

import net.playq.tk.postgres.RawSQL
import net.playq.tk.postgres.partitioning.PartitionKey

final case class TableName(schema: String, table: String) extends RawSQL {
  override def sqlString: String = s"${quoted(schema)}.${quoted(table)}"

  def partitionOf[K: PartitionKey](partitionKey: K): PartitionName = {
    PartitionKey[K].partitionOf(this, partitionKey)
  }
}
