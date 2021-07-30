package net.playq.tk.postgres.partitioning

trait PartitionedWrite[K] {
  def partitionKey: K
}
