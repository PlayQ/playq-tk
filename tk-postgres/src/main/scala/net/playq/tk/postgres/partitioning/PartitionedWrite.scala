package net.playq.tk.postgres.partitioning

trait PartitionedWrite[-T, +K] {
  def getPartitionKey(t: T): K
}
