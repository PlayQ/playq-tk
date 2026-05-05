package net.playq.tk.kafka

import izumi.distage.model.provisioning.IntegrationCheck
import izumi.functional.bio.Error2
import izumi.fundamentals.platform.integration.ResourceCheck
import logstage.LogIO2
import net.playq.tk.kafka.clients.KafkaClients
import net.playq.tk.kafka.config.KafkaPropsConfig

final class KafkaChecker[F[+_, +_]: Error2](
  kafkaClients: KafkaClients[F],
  kafkaConf: KafkaPropsConfig,
  log: LogIO2[F],
) extends IntegrationCheck[F[Throwable, _]] {

  override def resourcesAvailable(): F[Nothing, ResourceCheck] = {
    kafkaClients
      .allocateConsumer(propsOverride = Map.empty)
      .use(_.listTopics.as(ResourceCheck.Success()))
      .catchAll(
        error =>
          log
            .error(s"Kafka availability check failed: Couldn't list kafka topics for $kafkaConf due to $error skipping test")
            .as(ResourceCheck.ResourceUnavailable(s"Couldn't list kafka topics for kafkaConf=$kafkaConf", Some(error)))
      )
  }
}
