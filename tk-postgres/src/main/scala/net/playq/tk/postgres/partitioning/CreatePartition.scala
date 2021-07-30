package net.playq.tk.postgres.partitioning

import doobie.free.connection.ConnectionIO
import net.playq.tk.postgres.partitioning.model.TableName

trait CreatePartition[K] {
  def apply(table: TableName, partitionKey: K): ConnectionIO[Unit]
}

object CreatePartition {
  def apply[K](fn: (TableName, K) => ConnectionIO[Unit]): CreatePartition[K] = fn(_, _)
}
