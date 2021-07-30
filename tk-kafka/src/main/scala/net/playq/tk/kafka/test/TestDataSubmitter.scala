package net.playq.tk.kafka.test

import cats.effect.Blocker
import fs2.Stream
import io.circe.Encoder
import io.circe.syntax._
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BlockingIO2, F, IO2}
import izumi.fundamentals.platform.language.Quirks._
import logstage.LogIO2
import net.playq.tk.fs2kafka._
import net.playq.tk.kafka.config.KafkaPropsConfig
import net.playq.tk.quantified.{AsyncThrowable, ContextShiftThrowable}
import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition

import scala.jdk.CollectionConverters._
import scala.util.Random
import scala.util.chaining._

trait TestDataSubmitter[F[_, _]] {
  def submit[T: Encoder](topicPartition: TopicPartition, jsons: Iterable[T]): F[Throwable, Unit]

  def withTopic[A](topic: String, partitions: Int)(use: F[Throwable, A]): F[Throwable, A]
  def withRandomTopic[A](partitions: Int)(use: String => F[Throwable, A]): F[Throwable, A]
}

object TestDataSubmitter {
  final class Impl[F[+_, +_]: IO2: BlockingIO2: AsyncThrowable: ContextShiftThrowable](
    blocker: Blocker,
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
              .pipe(stringProducerF(props).sendAllWaitWithClose(blocker))
              .compile.drain
          }

          override def withTopic[A](topic: String, partitions: Int)(use: F[Throwable, A]): F[Throwable, A] = {
            F.bracket(
              acquire = createTopic(topic, partitions)
            )(release = _ => deleteTopic(topic).orTerminate)(
              use = _ => use
            )
          }

          override def withRandomTopic[A](partitions: Int)(use: String => F[Throwable, A]): F[Throwable, A] = {
            randomTopic.flatMap {
              topic =>
                withTopic(topic, partitions)(use(topic))
            }
          }

          private[this] def createTopic(topic: String, partitions: Int): F[Throwable, Unit] = {
            F.syncBlocking {
              admin
                .createTopics(List(new NewTopic(topic, partitions, 1)).asJava)
                .all().get().discard()
            }
          }

          private[this] def deleteTopic(topic: String): F[Throwable, Unit] = {
            F.syncBlocking {
              admin
                .deleteTopics(List(topic).asJava)
                .all().get().discard()
            }
          }

          private[this] val randomTopic: F[Nothing, String] = {
            F.sync(Random.alphanumeric.take(10).mkString)
          }
        }
    })

}
