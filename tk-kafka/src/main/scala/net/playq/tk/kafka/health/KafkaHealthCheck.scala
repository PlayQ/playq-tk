package net.playq.tk.kafka.health

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Error2, F}
import logstage.LogIO2
import net.playq.tk.health.{HealthChecker, TkHealthCheckStatus, TkHealthState}
import net.playq.tk.kafka.clients.KafkaClients

trait KafkaHealthCheck[F[+_, +_]] extends HealthChecker[F]

object KafkaHealthCheck {

  final class Impl[F[+_, +_]: Error2](
    kafkaClients: KafkaClients[F],
    log: LogIO2[F],
  ) extends Lifecycle.Of[F[Throwable, _], KafkaHealthCheck[F]](for {
      consumer <- kafkaClients.allocateConsumer()
    } yield new KafkaHealthCheck[F] {
      override def healthCheck(): F[Throwable, Set[TkHealthCheckStatus]] = {
        consumer.listTopics
          .redeem(
            err  = exception => log.crit(s"Error while health checking Kafka with $exception").as(TkHealthState.DEFUNCT),
            succ = _ => F.pure(TkHealthState.OK),
          ).map(status => Set(TkHealthCheckStatus("kafka.session", status)))
      }
    })
}
