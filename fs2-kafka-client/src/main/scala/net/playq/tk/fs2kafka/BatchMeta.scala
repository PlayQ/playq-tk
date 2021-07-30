package net.playq.tk.fs2kafka

import org.apache.kafka.common.TopicPartition

final case class BatchMeta(
  topic: String,
  partition: Partition,
  firstOffset: Offset,
  lastOffset: Offset,
  commitOffset: CommitOffset,
) {
  def topicPartition: TopicPartition = new TopicPartition(topic, partition)

  def commitEntry: (TopicPartition, CommitOffset) = topicPartition -> commitOffset
}

object BatchMeta {
  def fromKafkaData[T](batch: Iterable[KafkaData[T]]): Iterable[BatchMeta] =
    zipKafkaData(batch).map(_._1)

  def zipKafkaData[T](batch: Iterable[KafkaData[T]]): Iterable[(BatchMeta, Iterable[KafkaData[T]])] = {
    batch.groupBy(_.topicPartition).map {
      case (topicPartition, events) =>
        val firstOffset  = events.minBy(_.offset).offset
        val lastOffset   = events.maxBy(_.offset).offset
        val commitOffset = lastOffset + 1

        BatchMeta(topicPartition.topic, topicPartition.partition, firstOffset, lastOffset, commitOffset) -> events
    }
  }
}
