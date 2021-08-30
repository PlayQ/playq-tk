package net.playq.tk.fs2kafka

import cats.Applicative
import logstage.LogIO
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRecords}

trait PollLogger[F[_]] {
  def preLog(consumer: Consumer[?, ?], timeout: Long): F[Unit]
  def postLog(consumer: Consumer[?, ?], records: ConsumerRecords[?, ?], timeout: Long): F[Unit]
}

object PollLogger {
  def empty[F[_]](implicit F: Applicative[F]): PollLogger[F] =
    new PollLogger[F] {
      override def preLog(consumer: Consumer[?, ?], timeout: Offset): F[Unit]                                  = F.unit
      override def postLog(consumer: Consumer[?, ?], records: ConsumerRecords[?, ?], timeout: Offset): F[Unit] = F.unit
    }

  def logger[F[_]](topic: String)(implicit log: LogIO[F]): PollLogger[F] =
    new PollLogger[F] {
      override def preLog(consumer: Consumer[?, ?], timeout: Long): F[Unit] =
        log.info(s"Polling $topic with $timeout")

      override def postLog(consumer: Consumer[?, ?], records: ConsumerRecords[?, ?], timeout: Long): F[Unit] =
        log.info(s"Polled $topic successfully, got ${records.count}")
    }
}
