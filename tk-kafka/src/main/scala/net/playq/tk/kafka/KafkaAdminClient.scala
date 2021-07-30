package net.playq.tk.kafka

import cats.effect.{Blocker, Resource}
import cats.syntax.flatMap._
import izumi.functional.bio.BlockingIO2
import logstage.LogIO
import net.playq.tk.fs2kafka.{Partition, _}
import net.playq.tk.kafka.KafkaAdminClient.AdminClient
import net.playq.tk.kafka.config.KafkaPropsConfig
import net.playq.tk.quantified.{ContextShiftThrowable, SyncThrowable}
import org.apache.kafka.clients.producer.Producer

import scala.jdk.CollectionConverters._

trait KafkaAdminClient[F[_, _]] {
  def allocate: Resource[F[Throwable, ?], AdminClient[F]]
}

object KafkaAdminClient {

  trait AdminClient[F[_, _]] {
    def fetchPartitions(topic: String): F[Throwable, Set[Partition]]
  }

  final class Impl[F[+_, +_]: SyncThrowable: ContextShiftThrowable](
    kafkaConf: KafkaPropsConfig,
    log: LogIO[F[Throwable, ?]],
    blocker: Blocker,
    blockingIO: BlockingIO2[F],
  ) extends KafkaAdminClient[F] {

    override def allocate: Resource[F[Throwable, ?], AdminClient[F]] =
      stringProducerF(kafkaConf.getProducerProps).resource(blocker).map(adminClient)

    private[this] def adminClient(admin: Producer[_, _]): AdminClient[F] =
      new AdminClient[F] {
        override def fetchPartitions(topic: String): F[Throwable, Set[Partition]] = {
          log.info(s"Querying partitions for $topic $kafkaConf") >>
          blockingIO.syncBlocking(admin.partitionsFor(topic).asScala.map(_.partition).toSet)
        }
      }
  }
}
