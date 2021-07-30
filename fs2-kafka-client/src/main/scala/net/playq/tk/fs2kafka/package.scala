package net.playq.tk

import cats.effect._
import cats.syntax.flatMap._
import fs2.{Pipe, Pure, Stream}
import org.apache.kafka.clients.consumer._
import org.apache.kafka.clients.producer.{KafkaProducer, Producer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.jdk.CollectionConverters._

/** yet another fs2-kafka adapter */
package object fs2kafka {

  final type Stream2[+F[_, _], +A]  = Stream[F[Throwable, ?], A]
  final type Pipe2[F[_, _], -A, +B] = Pipe[F[Throwable, ?], A, B]

  final type Partition    = Int
  final type Offset       = Long
  final type CommitOffset = Long

  implicit final class StreamKafkaConsumerCtor[R[_], K, V](private val consumerCtor: R[Consumer[K, V]]) extends AnyVal {

    def resource[F[x] >: R[x]](blocker: Blocker)(implicit F: Sync[F], shift: ContextShift[F]): Resource[F, Consumer[K, V]] =
      Resource.make[F, Consumer[K, V]](
        acquire = consumerCtor
      )(release = c => shift.blockOn(blocker)(F.delay(c.synchronized(c.close()))))

    def consumerStreamWithClose[F[x] >: R[x]: Sync: ContextShift](blocker: Blocker): Stream[F, Consumer[K, V]] =
      Stream.resource(resource[F](blocker))

    def streamWithClose[F[x] >: R[x]: Sync: ContextShift](blocker: Blocker)(timeout: Long, log: PollLogger[F]): Stream[F, ConsumerRecords[K, V]] =
      consumerStreamWithClose[F](blocker)
        .flatMap(_.stream[F](blocker)(timeout, log))

    def streamFlattenWithClose[F[x] >: R[x]: Sync: ContextShift](blocker: Blocker)(timeoutMs: Long, log: PollLogger[F]): Stream[F, KafkaData[V]] =
      streamWithClose[F](blocker)(timeoutMs, log)
        .flatMap(flattenConsumerRecords)
        .map(KafkaData(_))

    def streamConsumerWithClose[F[x] >: R[x]: Sync: ContextShift](
      blocker: Blocker
    )(timeoutMs: Long,
      log: PollLogger[F],
    ): Stream[F, (Consumer[K, V], ConsumerRecords[K, V])] =
      consumerStreamWithClose[F](blocker).flatMap {
        consumer =>
          consumer
            .stream[F](blocker)(timeoutMs, log)
            .map(consumer -> _)
      }

    def finiteStreamConsumerWithClose[F[x] >: R[x]: Sync: ContextShift](
      blocker: Blocker
    )(timeoutMs: Long,
      log: PollLogger[F],
    ): Stream[F, (Consumer[K, V], ConsumerRecords[K, V])] =
      consumerStreamWithClose[F](blocker).flatMap {
        consumer =>
          consumer
            .finiteStream[F](blocker)(timeoutMs, log)
            .map(consumer -> _)
      }

    def streamFlattenConsumerWithClose[F[x] >: R[x]: Sync: ContextShift](
      blocker: Blocker
    )(timeoutMs: Long,
      log: PollLogger[F],
    ): Stream[F, (Consumer[K, V], KafkaData[V])] =
      consumerStreamWithClose[F](blocker).flatMap {
        consumer =>
          consumer
            .stream[F](blocker)(timeoutMs, log)
            .flatMap(flattenConsumerRecords)
            .map(rec => consumer -> KafkaData(rec))
      }

    def finiteStreamFlattenConsumerWithClose[F[x] >: R[x]: Sync: ContextShift](
      blocker: Blocker
    )(timeoutsMs: Long,
      log: PollLogger[F],
    ): Stream[F, (Consumer[K, V], KafkaData[V])] =
      consumerStreamWithClose[F](blocker).flatMap {
        consumer =>
          consumer
            .finiteStream[F](blocker)(timeoutsMs, log)
            .flatMap(flattenConsumerRecords)
            .map(rec => consumer -> KafkaData(rec))
      }
  }

  implicit final class StreamKafkaClient[K, V](private val consumer: Consumer[K, V]) extends AnyVal {

    def commitF[F[_]](blocker: Blocker)(implicit F: Sync[F], shift: ContextShift[F]): F[Unit] =
      shift.blockOn(blocker) {
        F.delay {
          consumer.synchronized {
            consumer.commitSync()
          }
        }
      }

    def commitF[F[_]](blocker: Blocker, map: Map[TopicPartition, CommitOffset])(implicit F: Sync[F], shift: ContextShift[F]): F[Unit] =
      shift.blockOn(blocker) {
        F.delay {
          consumer.synchronized {
            consumer.commitSync(map.view.mapValues(new OffsetAndMetadata(_)).toMap.asJava)
          }
        }
      }

    /**
      * Infinite stream that filters empty data.
      */
    def stream[F[_]](blocker: Blocker)(timeout: Long, log: PollLogger[F])(implicit F: Sync[F], shift: ContextShift[F]): Stream[F, ConsumerRecords[K, V]] =
      _stream(blocker)(timeout, log).filter(!_.isEmpty)

    /**
      * Finite stream that stops when an empty data was received.
      */
    def finiteStream[F[_]](blocker: Blocker)(timeout: Long, log: PollLogger[F])(implicit F: Sync[F], shift: ContextShift[F]): Stream[F, ConsumerRecords[K, V]] =
      _stream(blocker)(timeout, log).takeWhile(!_.isEmpty)

    private def _stream[F[_]](blocker: Blocker)(timeout: Long, log: PollLogger[F])(implicit F: Sync[F], shift: ContextShift[F]): Stream[F, ConsumerRecords[K, V]] = {
      val poll = shift.blockOn(blocker) {
        log.preLog(consumer, timeout) >>
        F.delay {
          consumer.synchronized {
            consumer.poll(timeout)
          }
        }.flatTap(log.postLog(consumer, _, timeout))
      }

      Stream.repeatEval(poll)
    }

    def streamFlatten[F[_]: Sync: ContextShift](blocker: Blocker)(timeout: Long, log: PollLogger[F]): Stream[F, KafkaData[V]] = {
      stream[F](blocker)(timeout, log)
        .flatMap(flattenConsumerRecords)
        .map(KafkaData(_))
    }

  }

  implicit final class StreamKafkaProducerCtor[R[_], K, V](private val producerCtor: R[Producer[K, V]]) extends AnyVal {

    def resource[F[x] >: R[x]](blocker: Blocker)(implicit F: Sync[F], shift: ContextShift[F]): Resource[F, Producer[K, V]] =
      Resource.make[F, Producer[K, V]](
        acquire = producerCtor
      )(release = p => shift.blockOn(blocker)(F.delay(p.synchronized(p.close()))))

    def producerStreamWithClose[F[x] >: R[x]: Sync: ContextShift](blocker: Blocker): Stream[F, Producer[K, V]] =
      Stream.resource(resource[F](blocker))

    def sendAllWithClose[F[x] >: R[x]: Sync: ContextShift](blocker: Blocker): Pipe[F, ProducerRecord[K, V], java.util.concurrent.Future[RecordMetadata]] =
      msgStream =>
        producerStreamWithClose[F](blocker)
          .flatMap(msgStream evalMap _.sendF[F])

    def sendAllWaitWithClose[F[x] >: R[x]: Async: ContextShift](blocker: Blocker): Pipe[F, ProducerRecord[K, V], RecordMetadata] =
      msgStream =>
        producerStreamWithClose[F](blocker)
          .flatMap(msgStream evalMap _.sendWait[F])
  }

  implicit final class StreamKafkaProducer[K, V](private val producer: Producer[K, V]) extends AnyVal {

    def sendF[F[_]](record: ProducerRecord[K, V])(implicit F: Sync[F]): F[java.util.concurrent.Future[RecordMetadata]] =
      F.delay(producer.send(record))

    def sendWait[F[_]](record: ProducerRecord[K, V])(implicit F: Async[F]): F[RecordMetadata] =
      F.async {
        cb =>
          val _ = producer.send(
            record,
            (metadata: RecordMetadata, exception: Exception) =>
              exception match {
                case null => cb(Right(metadata))
                case _    => cb(Left(exception))
              },
          )
      }

    def sendAll[F[_]: Sync]: Pipe[F, ProducerRecord[K, V], java.util.concurrent.Future[RecordMetadata]] =
      _.evalMap(producer.sendF[F])

    def sendAllWait[F[_]: Async]: Pipe[F, ProducerRecord[K, V], RecordMetadata] =
      _.evalMap(producer.sendWait[F])
  }

  def flattenConsumerRecords[K, V](records: ConsumerRecords[K, V]): Stream[Pure, ConsumerRecord[K, V]] =
    Stream.emits(flattenConsumerRecords0(records))

  def flattenConsumerRecords0[K, V](records: ConsumerRecords[K, V]): List[ConsumerRecord[K, V]] =
    records.iterator.asScala.toList

  def stringConsumer(props: Map[String, Any]): Consumer[String, String] =
    new KafkaConsumer(props.asJava, new StringDeserializer, new StringDeserializer)

  def stringProducer(props: Map[String, Any]): Producer[String, String] =
    new KafkaProducer(props.asJava, new StringSerializer, new StringSerializer)

  def stringConsumerF[F[_]](props: Map[String, Any])(implicit F: Sync[F]): F[Consumer[String, String]] =
    F.delay(stringConsumer(props))

  def stringProducerF[F[_]](props: Map[String, Any])(implicit F: Sync[F]): F[Producer[String, String]] =
    F.delay(stringProducer(props))

}
