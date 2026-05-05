package net.playq.tk.kafka.clients

import fs2.{Pure, Stream}
import izumi.functional.bio.catz.BIOToMonadCancel
import izumi.functional.bio.{BlockingIO2, F, IO2}
import izumi.functional.lifecycle.Lifecycle
import net.playq.tk.kafka.data.{CommitOffset, KafkaData, KafkaDeserializer, Offset, Stream2}
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.{PartitionInfo, TopicPartition}

import java.time.Duration
import scala.jdk.CollectionConverters.{IteratorHasAsScala, ListHasAsScala, MapHasAsJava, MapHasAsScala, SeqHasAsJava, SetHasAsJava}

trait TkKafkaConsumer[F[+_, +_], K, V] {
  def commit(values: Map[TopicPartition, CommitOffset]): F[Throwable, Unit]

  def infiniteStream(timeout: Long, log: PollLogger[F]): Stream2[F, ConsumerRecords[K, V]]
  def infiniteStreamFlatten(timeout: Long, log: PollLogger[F]): Stream2[F, KafkaData[V]]

  def finiteStream(timeout: Long, log: PollLogger[F]): Stream2[F, ConsumerRecords[K, V]]
  def finiteStreamFlatten(timeout: Long, log: PollLogger[F]): Stream2[F, KafkaData[V]]

  def seekToBeginning(partitions: List[TopicPartition]): F[Throwable, Unit]
  def listTopics: F[Throwable, Map[String, List[PartitionInfo]]]
  def poll(timeout: Duration): F[Throwable, ConsumerRecords[K, V]]

  def subscribe(topics: List[String]): F[Throwable, Unit]
  def assign(partitions: List[TopicPartition]): F[Throwable, Unit]
  def seek(topicPartition: TopicPartition, offset: Offset): F[Throwable, Unit]
  def position(topicPartition: TopicPartition): F[Throwable, Offset]
  def endOffsets(partitions: List[TopicPartition]): F[Throwable, Map[TopicPartition, Long]]
  def committed(partitions: Set[TopicPartition]): F[Throwable, Map[TopicPartition, OffsetAndMetadata]]
}

trait TkKafkaConsumerLifecycle[F[+_, +_], K, V] extends Lifecycle[F[Throwable, _], TkKafkaConsumer[F, K, V]] {
  def infiniteStreamFlatten(timeout: Long, log: PollLogger[F]): Stream2[F, KafkaData[V]]
  def finiteStreamFlatten(timeout: Long, log: PollLogger[F]): Stream2[F, KafkaData[V]]

  def infiniteStreamFlattenConsumer(timeoutMs: Long, log: PollLogger[F]): Stream2[F, (TkKafkaConsumer[F, K, V], KafkaData[V])]
  def finiteStreamFlattenConsumer(timeoutMs: Long, log: PollLogger[F]): Stream2[F, (TkKafkaConsumer[F, K, V], KafkaData[V])]

  // same as underlying Lifecycle methods, but these don't require an IO2[F] in scope
  def toStream: Stream2[F, TkKafkaConsumer[F, K, V]]
  def use[A](f: TkKafkaConsumer[F, K, V] => F[Throwable, A]): F[Throwable, A]
  def evalTap(f: TkKafkaConsumer[F, K, V] => F[Throwable, Unit]): TkKafkaConsumerLifecycle[F, K, V]
}

object TkKafkaConsumer {
  def wrap[F[+_, +_]: IO2: BlockingIO2, K, V](
    consumer: Consumer[K, V]
  ): TkKafkaConsumer[F, K, V] = {
    new TkKafkaConsumerImpl(consumer)
  }

  def allocate[F[+_, +_]: IO2: BlockingIO2, K: KafkaDeserializer, V: KafkaDeserializer](
    props: Map[String, AnyRef]
  ): TkKafkaConsumerLifecycle[F, K, V] = {
    new TkKafkaConsumerLifecycleImpl[F, K, V](allocateRaw[F, K, V](props).map(wrap(_)))
  }

  def allocateRaw[F[+_, +_]: IO2: BlockingIO2, K: KafkaDeserializer, V: KafkaDeserializer](
    props: Map[String, AnyRef]
  ): Lifecycle[F[Throwable, _], Consumer[K, V]] = {
    Lifecycle
      .make(
        F.syncThrowable(new KafkaConsumer(props.asJava, KafkaDeserializer[K].underlying, KafkaDeserializer[V].underlying))
      )(c => F.syncBlocking(c.synchronized(c.close())))
  }

  def flattenConsumerRecords[K, V](records: ConsumerRecords[K, V]): Stream[Pure, ConsumerRecord[K, V]] = {
    Stream.emits(records.iterator.asScala.toList)
  }

  private final class TkKafkaConsumerLifecycleImpl[F[+_, +_]: IO2, K, V](
    override val lifecycle: Lifecycle[F[Throwable, _], TkKafkaConsumer[F, K, V]]
  ) extends TkKafkaConsumerLifecycle[F, K, V]
    with Lifecycle.OfInner[F[Throwable, _], TkKafkaConsumer[F, K, V]] {

    override def infiniteStreamFlatten(timeout: Long, log: PollLogger[F]): Stream2[F, KafkaData[V]] = {
      toStream.flatMap(_.infiniteStreamFlatten(timeout, log))
    }

    override def finiteStreamFlatten(timeout: Long, log: PollLogger[F]): Stream2[F, KafkaData[V]] = {
      toStream.flatMap(_.finiteStreamFlatten(timeout, log))
    }

    override def infiniteStreamFlattenConsumer(timeoutMs: Long, log: PollLogger[F]): Stream2[F, (TkKafkaConsumer[F, K, V], KafkaData[V])] = {
      toStream.flatMap(consumer => consumer.infiniteStreamFlatten(timeoutMs, log).map(consumer -> _))
    }

    override def finiteStreamFlattenConsumer(timeoutMs: Long, log: PollLogger[F]): Stream2[F, (TkKafkaConsumer[F, K, V], KafkaData[V])] = {
      toStream.flatMap(consumer => consumer.finiteStreamFlatten(timeoutMs, log).map(consumer -> _))
    }

    override def toStream: Stream2[F, TkKafkaConsumer[F, K, V]] = {
      Stream.resource(lifecycle.toCats)
    }

    override def use[A](f: TkKafkaConsumer[F, K, V] => F[Throwable, A]): F[Throwable, A] = {
      lifecycle.use(f)
    }

    override def evalTap(f: TkKafkaConsumer[F, K, V] => F[Throwable, Unit]): TkKafkaConsumerLifecycle[F, K, V] = {
      new TkKafkaConsumerLifecycleImpl(lifecycle.evalTap(f))
    }
  }

  private final class TkKafkaConsumerImpl[F[+_, +_]: IO2: BlockingIO2, K, V](
    consumer: Consumer[K, V]
  ) extends TkKafkaConsumer[F, K, V] {

    override def commit(values: Map[TopicPartition, CommitOffset]): F[Throwable, Unit] = {
      consumerCall(_.commitSync(values.view.mapValues(new OffsetAndMetadata(_)).toMap.asJava))
    }

    override def infiniteStream(timeout: CommitOffset, log: PollLogger[F]): Stream2[F, ConsumerRecords[K, V]] = {
      _stream(timeout, log).filter(!_.isEmpty)
    }

    override def finiteStream(timeout: Long, log: PollLogger[F]): Stream2[F, ConsumerRecords[K, V]] = {
      _stream(timeout, log).takeWhile(!_.isEmpty)
    }

    override def infiniteStreamFlatten(timeout: CommitOffset, log: PollLogger[F]): Stream2[F, KafkaData[V]] = {
      infiniteStream(timeout, log)
        .flatMap(flattenConsumerRecords)
        .map(KafkaData(_))
    }

    override def finiteStreamFlatten(timeout: CommitOffset, log: PollLogger[F]): Stream2[F, KafkaData[V]] = {
      finiteStream(timeout, log)
        .flatMap(flattenConsumerRecords)
        .map(KafkaData(_))
    }

    override def seek(topicPartition: TopicPartition, offset: Offset): F[Throwable, Unit] = {
      consumerCall(_.seek(topicPartition, offset))
    }

    override def seekToBeginning(partitions: List[TopicPartition]): F[Throwable, Unit] = {
      consumerCall(_.seekToBeginning(partitions.asJava))
    }

    override def listTopics: F[Throwable, Map[String, List[PartitionInfo]]] = {
      consumerCall(_.listTopics().asScala.toMap.view.mapValues(_.asScala.toList).toMap)
    }

    override def subscribe(topics: List[String]): F[Throwable, Unit] = {
      consumerCall(_.subscribe(topics.asJava))
    }

    override def poll(timeout: Duration): F[Throwable, ConsumerRecords[K, V]] = {
      consumerCall(_.poll(timeout))
    }

    override def assign(partitions: List[TopicPartition]): F[Throwable, Unit] = {
      consumerCall(_.assign(partitions.asJava))
    }

    override def position(topicPartition: TopicPartition): F[Throwable, Offset] = {
      consumerCall(_.position(topicPartition))
    }

    override def endOffsets(partitions: List[TopicPartition]): F[Throwable, Map[TopicPartition, Offset]] = {
      consumerCall(_.endOffsets(partitions.asJava)).map(_.asScala.toMap.view.mapValues(_.longValue()).toMap)
    }

    override def committed(partitions: Set[TopicPartition]): F[Throwable, Map[TopicPartition, OffsetAndMetadata]] = {
      consumerCall(_.committed(partitions.asJava)).map(_.asScala.toMap)
    }

    private def consumerCall[A](f: Consumer[K, V] => A): F[Throwable, A] = {
      F.syncBlocking(consumer.synchronized(f(consumer)))
    }

    private def _stream(
      timeout: Long,
      log: PollLogger[F],
    ): Stream2[F, ConsumerRecords[K, V]] = {
      Stream.repeatEval {
        log.preLog(this, timeout)
          *> poll(Duration.ofMillis(timeout))
            .tap(log.postLog(this, _, timeout))
      }
    }
  }

}
