package net.playq.tk.kafka.clients

import izumi.functional.bio.{Applicative2, F}
import logstage.LogIO2
import net.playq.tk.kafka.data.Offset
import org.apache.kafka.clients.consumer.ConsumerRecords

trait PollLogger[F[+_, +_]] {
  def preLog(consumer: TkKafkaConsumer[F, ?, ?], timeout: Long): F[Throwable, Unit]
  def postLog(consumer: TkKafkaConsumer[F, ?, ?], records: ConsumerRecords[?, ?], timeout: Long): F[Throwable, Unit]
}

object PollLogger {
  def empty[F[+_, +_]: Applicative2]: PollLogger[F] =
    new PollLogger[F] {
      override def preLog(consumer: TkKafkaConsumer[F, ?, ?], timeout: Offset): F[Throwable, Unit]                                  = F.unit
      override def postLog(consumer: TkKafkaConsumer[F, ?, ?], records: ConsumerRecords[?, ?], timeout: Offset): F[Throwable, Unit] = F.unit
    }

  def logger[F[+_, +_]](topic: String)(implicit log: LogIO2[F]): PollLogger[F] =
    new PollLogger[F] {
      override def preLog(consumer: TkKafkaConsumer[F, ?, ?], timeout: Long): F[Throwable, Unit] = {
        log.info(s"Polling $topic with $timeout")
      }
      override def postLog(consumer: TkKafkaConsumer[F, ?, ?], records: ConsumerRecords[?, ?], timeout: Long): F[Throwable, Unit] = {
        log.info(s"Polled $topic successfully, got ${records.count}")
      }
    }
}
