package net.playq.tk.kafka

import net.playq.tk.fs2kafka.stringConsumerF
import izumi.distage.framework.model.IntegrationCheck
import izumi.functional.bio.catz.*
import izumi.functional.bio.{F, IO2}
import izumi.fundamentals.platform.integration.ResourceCheck
import logstage.LogIO2
import net.playq.tk.kafka.config.KafkaPropsConfig

final class KafkaChecker[F[+_, +_]: IO2](
  kafkaConf: KafkaPropsConfig,
  log: LogIO2[F],
) extends IntegrationCheck[F[Throwable, _]] {

  @SuppressWarnings(Array("CatchThrowable"))
  override def resourcesAvailable(): F[Throwable, ResourceCheck] = {
    stringConsumerF[F[Throwable, _]](kafkaConf.getConsumerProps).bracketAuto {
      consumer =>
        F.syncThrowable {
          consumer.listTopics()
          ResourceCheck.Success()
        }
    }.catchAll {
      error =>
        log
          .error(s"Kafka availability check failed: Couldn't list kafka topics for $kafkaConf due to $error skipping test")
          .as(ResourceCheck.ResourceUnavailable(s"Couldn't list kafka topics for kafkaConf=$kafkaConf", Some(error)))
    }
  }

}
