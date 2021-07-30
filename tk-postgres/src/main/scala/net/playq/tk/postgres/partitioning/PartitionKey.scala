package net.playq.tk.postgres.partitioning

import net.playq.tk.postgres.partitioning.model.{PartitionName, TableName}

import java.util.UUID

trait PartitionKey[K] {
  protected def partitionTableName(table: String, key: K): String

  final def partitionOf(table: TableName, partitionKey: K): PartitionName = {
    PartitionName(table.schema, partitionTableName(table.table, partitionKey))
  }

  final def contramap[A](f: A => K): PartitionKey[A] = {
    (table, key) => partitionTableName(table, f(key))
  }
}

object PartitionKey {
  def apply[K: PartitionKey]: PartitionKey[K] = implicitly

  /** note, postgres has a limit on table name length, we trim UUID to not exceed it */
  def uuidPrefix(uuid: UUID): String = {
    uuid.toString.take(8)
  }
}
