package net.playq.tk.kafka

import io.circe.Encoder
import io.circe.syntax.*
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.Temporal2
import net.playq.tk.kafka.KafkaJsonProducerComponent.KafkaJsonProducer
import net.playq.tk.kafka.clients.{KafkaClients, TkKafkaProducer}
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}

import scala.concurrent.TimeoutException
import scala.concurrent.duration.FiniteDuration

abstract class KafkaJsonProducerComponent[F[+_, +_]: Temporal2, In: Encoder, +A](
  kafkaClients: KafkaClients[F],
  timeout: FiniteDuration,
  clientId: Option[String] = None,
)(ctor: KafkaJsonProducer[F, In] => A
) extends Lifecycle.Of(for {
    producer <- kafkaClients.allocateProducer(
      propsOverride = clientId.map(ProducerConfig.CLIENT_ID_CONFIG -> _).toMap
    )
  } yield ctor(new KafkaJsonProducer[F, In](producer, timeout)))

object KafkaJsonProducerComponent {
  final class KafkaJsonProducer[F[+_, +_]: Temporal2, In: Encoder](producer: TkKafkaProducer[F, String, String], timeout: FiniteDuration) {
    def send(topic: String, item: In): F[Throwable, Unit] = {
      producer
        .sendWait(new ProducerRecord(topic, item.asJson.noSpaces))
        .timeoutFail(new TimeoutException(s"Kafka Producer send for topic=$topic item=$item timed out"))(timeout)
        .void
    }
  }
}
