package net.playq.tk.kafka.test

import fs2.Stream
import io.circe.Encoder
import io.circe.syntax.*
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, BlockingIO2, F, IO2}
import izumi.fundamentals.platform.language.Quirks.*
import logstage.LogIO2
import net.playq.tk.kafka.clients.KafkaClients
import net.playq.tk.kafka.config.KafkaPropsConfig
import net.playq.tk.quantified.AsyncThrowable
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition

import scala.jdk.CollectionConverters.*
import scala.util.Random
import scala.util.chaining.*

trait TestDataSubmitter[F[_, _]] {
  def submit[T: Encoder](topicPartition: TopicPartition, jsons: Iterable[T]): F[Throwable, Unit]

  def withTopic[E >: Throwable, A](topic: String, partitions: Int)(use: F[E, A]): F[E, A]
  def withRandomTopic[E >: Throwable, A](partitions: Int)(use: String => F[E, A]): F[E, A]
}

object TestDataSubmitter {
  final class Impl[F[+_, +_]: Async2: BlockingIO2: AsyncThrowable](
    kafkaClients: KafkaClients[F],
    log: LogIO2[F],
  )(implicit
    kafkaConf: KafkaPropsConfig
  ) extends Lifecycle.Of(Lifecycle.fromAutoCloseable(IO2(AdminClient.create(kafkaConf.getProducerProps.asJava))).map {
      admin =>
        new TestDataSubmitter[F] {
          override def submit[T: Encoder](topicPartition: TopicPartition, jsons: Iterable[T]): F[Throwable, Unit] = {
            val props = kafkaConf.getProducerProps

            log.info(s"Sending jsons, ${jsons.size}") *>
            Stream
              .emits(jsons.toSeq)
              .map(_.asJson.noSpaces.pipe(new ProducerRecord(topicPartition.topic, topicPartition.partition, "k1", _)))
              .pipe(kafkaClients.allocateProducer[String, String](props).sendAllWait)
              .compile
              .drain
          }

          override def withTopic[E >: Throwable, A](topic: String, partitions: Int)(use: F[E, A]): F[E, A] = {
            F.bracket(
              acquire = createTopic(topic, partitions).widenError[E]
            )(release = _ => deleteTopic(topic).orTerminate)(
              use = _ => use
            )
          }

          override def withRandomTopic[E >: Throwable, A](partitions: Int)(use: String => F[E, A]): F[E, A] = {
            randomTopic.flatMap {
              topic =>
                withTopic[E, A](topic, partitions)(use(topic))
            }
          }

          private def createTopic(topic: String, partitions: Int): F[Throwable, Unit] = {
            F.syncBlocking {
              admin
                .createTopics(List(new NewTopic(topic, partitions, 1.toShort)).asJava)
                .all().get().discard()
            }
          }

          private def deleteTopic(topic: String): F[Throwable, Unit] = {
            F.syncBlocking {
              admin
                .deleteTopics(List(topic).asJava)
                .all().get().discard()
            }
          }

          private val randomTopic: F[Nothing, String] = {
            F.sync(Random.alphanumeric.take(10).mkString)
          }
        }
    })

}
