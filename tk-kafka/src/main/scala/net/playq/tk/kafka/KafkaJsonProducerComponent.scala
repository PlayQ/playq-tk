package net.playq.tk.kafka

import net.playq.tk.fs2kafka.{stringProducerF, _}
import io.circe.Encoder
import io.circe.syntax.*
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.catz.*
import izumi.functional.bio.{Async2, Temporal2}
import KafkaJsonProducerComponent.KafkaJsonProducer
import net.playq.tk.kafka.config.KafkaPropsConfig
import org.apache.kafka.clients.producer.{Producer, ProducerConfig, ProducerRecord}

import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

abstract class KafkaJsonProducerComponent[F[+_, +_]: Async2: Temporal2, In: Encoder, +A](
  kafkaPropsCfg: KafkaPropsConfig,
  timeout: FiniteDuration,
  clientId: Option[String] = None,
)(ctor: KafkaJsonProducer[F, In] => A
) extends Lifecycle.Of(for {
    producer <- Lifecycle.fromAutoCloseable {
      val idProps = clientId.map(ProducerConfig.CLIENT_ID_CONFIG -> _).toMap
      stringProducerF(props = kafkaPropsCfg.getProducerProps ++ idProps)
    }
  } yield ctor(new KafkaJsonProducer(producer, timeout)))

object KafkaJsonProducerComponent {
  final class KafkaJsonProducer[F[+_, +_]: Async2: Temporal2, In: Encoder](producer: Producer[String, String], timeout: FiniteDuration) {
    def send(topic: String, item: In): F[Throwable, Unit] = {
      producer
        .sendWait(new ProducerRecord(topic, item.asJson.noSpaces))
        .timeoutFail(new TimeoutException(s"Kafka Producer send for topic=$topic item=$item timed out"))(timeout)
        .void
    }
  }
}
