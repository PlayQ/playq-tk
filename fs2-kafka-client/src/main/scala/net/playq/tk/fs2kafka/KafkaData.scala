package net.playq.tk.fs2kafka

import cats.syntax.functor._
import cats.syntax.traverse._
import cats.{Applicative, Eval, Traverse}
import fs2.Stream
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRecord}
import org.apache.kafka.common.TopicPartition

final case class KafkaData[+A](topic: String, partition: Partition, offset: Offset, value: A) {
  def topicPartition: TopicPartition = new TopicPartition(topic, partition)

  def commitOffset: CommitOffset = offset + 1
}

object KafkaData {
  def apply[T](record: ConsumerRecord[_, T]): KafkaData[T] = {
    KafkaData(record.topic, record.partition, record.offset, record.value)
  }

  implicit final val kafkaDataTraverse: Traverse[KafkaData] = new Traverse[KafkaData] {
    override def map[A, B](fa: KafkaData[A])(f: A => B): KafkaData[B] =
      fa.copy(value = f(fa.value))

    override def traverse[G[_]: Applicative, A, B](fa: KafkaData[A])(f: A => G[B]): G[KafkaData[B]] =
      f(fa.value).map(a => fa.copy(value = a))

    override def foldLeft[A, B](fa: KafkaData[A], b: B)(f: (B, A) => B): B =
      f(b, fa.value)

    override def foldRight[A, B](fa: KafkaData[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      f(fa.value, lb)
  }

  implicit final class KafkaDataIterableOps[T](private val events: Iterable[KafkaData[T]]) extends AnyVal {
    def kafkaValues: Iterable[T] = events.map(_.value)

    def withBatchMeta: Iterable[(BatchMeta, Iterable[KafkaData[T]])] = BatchMeta.zipKafkaData(events)

    def firstOffsets: Map[TopicPartition, Offset] = events.groupBy(_.topicPartition).view.mapValues(_.minBy(_.offset).offset).toMap

    def lastOffsets: Map[TopicPartition, Offset] = events.groupBy(_.topicPartition).view.mapValues(_.maxBy(_.offset).offset).toMap

    def commitOffsets: Map[TopicPartition, CommitOffset] = events.groupBy(_.topicPartition).view.mapValues(_.maxBy(_.offset).commitOffset).toMap
  }

  implicit final class KafkaDataStreamOps[F[_], A](stream: Stream[F, KafkaData[A]]) {
    @inline def mapKafkaValues[B](f: A => B): Stream[F, KafkaData[B]] =
      stream.map(_.map(f))

    @inline def traverseKafkaValues[G[x] >: F[x]: Applicative, B](f: A => G[B]): Stream[G, KafkaData[B]] =
      stream.evalMap(_.traverse(f))
  }

  implicit final class ConsumerKafkaDataStreamOps[F[_], K, V, A](stream: Stream[F, (Consumer[K, V], KafkaData[A])]) {
    @inline def mapKafkaValues[B](f: A => B): Stream[F, (Consumer[K, V], KafkaData[B])] =
      stream.map { case (k, v) => k -> v.map(f) }

    @inline def traverseKafkaValues[G[x] >: F[x]: Applicative, B](f: A => G[B]): Stream[G, (Consumer[K, V], KafkaData[B])] =
      stream.evalMap[G, (Consumer[K, V], KafkaData[B])] {
        case (k, v) => v.traverse(f).map(k -> _)
      }
  }
}
