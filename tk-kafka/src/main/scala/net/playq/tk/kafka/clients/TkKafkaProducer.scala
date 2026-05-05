package net.playq.tk.kafka.clients

import fs2.Stream
import izumi.functional.bio.catz.BIOToMonadCancel
import izumi.functional.bio.{Async2, BlockingIO2, F, IO2}
import izumi.functional.lifecycle.Lifecycle
import net.playq.tk.kafka.data.{KafkaSerializer, Partition, Pipe2}
import org.apache.kafka.clients.producer.{KafkaProducer, Producer, ProducerRecord, RecordMetadata}

import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsJava}

trait TkKafkaProducer[F[+_, +_], K, V] {
  def sendWait(record: ProducerRecord[K, V]): F[Throwable, RecordMetadata]
  def sendAllWait: Pipe2[F, ProducerRecord[K, V], RecordMetadata]

  def partitionsFor(topic: String): F[Throwable, Set[Partition]]
}

trait TkKafkaProducerLifecycle[F[+_, +_], K, V] extends Lifecycle[F[Throwable, _], TkKafkaProducer[F, K, V]] {
  def sendAllWait: Pipe2[F, ProducerRecord[K, V], RecordMetadata]

  // same as underlying Lifecycle methods, but these don't require an IO2[F] in scope
  def toStream: Stream[F[Throwable, _], TkKafkaProducer[F, K, V]]
  def use[A](f: TkKafkaProducer[F, K, V] => F[Throwable, A]): F[Throwable, A]
  def evalTap(f: TkKafkaProducer[F, K, V] => F[Throwable, Unit]): TkKafkaProducerLifecycle[F, K, V]
}

object TkKafkaProducer {
  def wrap[F[+_, +_]: Async2: BlockingIO2, K, V](producer: Producer[K, V]): TkKafkaProducer[F, K, V] = {
    new TkKafkaProducerImpl[F, K, V](producer)
  }

  def allocate[F[+_, +_]: Async2: BlockingIO2, K: KafkaSerializer, V: KafkaSerializer](
    props: Map[String, AnyRef]
  ): TkKafkaProducerLifecycle[F, K, V] = {
    new TkKafkaProducerLifecycleImpl[F, K, V](allocateRaw[F, K, V](props).map(wrap(_)))
  }

  def allocateRaw[F[+_, +_]: IO2: BlockingIO2, K: KafkaSerializer, V: KafkaSerializer](
    props: Map[String, AnyRef]
  ): Lifecycle[F[Throwable, _], Producer[K, V]] = {
    Lifecycle.make(
      acquire = F.syncThrowable(new KafkaProducer(props.asJava, KafkaSerializer[K].underlying, KafkaSerializer[V].underlying))
    )(release = p => F.syncBlocking(p.synchronized(p.close())))
  }

  private final class TkKafkaProducerLifecycleImpl[F[+_, +_]: IO2, K, V](
    override val lifecycle: Lifecycle[F[Throwable, _], TkKafkaProducer[F, K, V]]
  ) extends TkKafkaProducerLifecycle[F, K, V]
    with Lifecycle.OfInner[F[Throwable, _], TkKafkaProducer[F, K, V]] {

    override def sendAllWait: Pipe2[F, ProducerRecord[K, V], RecordMetadata] = {
      msgStream => toStream.flatMap(_.sendAllWait(msgStream))
    }

    override def toStream: Stream[F[Throwable, _], TkKafkaProducer[F, K, V]] = {
      Stream.resource(lifecycle.toCats)
    }

    override def use[A](f: TkKafkaProducer[F, K, V] => F[Throwable, A]): F[Throwable, A] = {
      lifecycle.use(f)
    }

    override def evalTap(f: TkKafkaProducer[F, K, V] => F[Throwable, Unit]): TkKafkaProducerLifecycle[F, K, V] = {
      new TkKafkaProducerLifecycleImpl(lifecycle.evalTap(f))
    }
  }

  private final class TkKafkaProducerImpl[F[+_, +_]: Async2: BlockingIO2, K, V](
    producer: Producer[K, V]
  ) extends TkKafkaProducer[F, K, V] {
    override def sendWait(record: ProducerRecord[K, V]): F[Throwable, RecordMetadata] = {
      sendWaitImpl(producer)(record)
    }
    override def sendAllWait: Pipe2[F, ProducerRecord[K, V], RecordMetadata] = {
      _.evalMap(sendWait)
    }
    override def partitionsFor(topic: String): F[Throwable, Set[Partition]] = {
      F.syncBlocking(producer.partitionsFor(topic).asScala.map(_.partition).toSet)
    }
  }

  private def sendWaitImpl[F[+_, +_]: Async2, K, V](producer: Producer[K, V])(record: ProducerRecord[K, V]): F[Throwable, RecordMetadata] = {
    F.async {
      cb =>
        producer.send(
          record,
          (metadata: RecordMetadata, exception: Exception) =>
            exception match {
              case null => cb(Right(metadata))
              case _    => cb(Left(exception))
            },
        )
        ()
    }
  }
}
