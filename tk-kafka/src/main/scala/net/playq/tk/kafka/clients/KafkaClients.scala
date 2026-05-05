package net.playq.tk.kafka.clients

import izumi.functional.bio.{Async2, BlockingIO2}
import net.playq.tk.kafka.config.KafkaPropsConfig
import net.playq.tk.kafka.data.{KafkaDeserializer, KafkaSerializer}

trait KafkaClients[F[+_, +_]] {
  def allocateProducer[K: KafkaSerializer, V: KafkaSerializer](propsOverride: Map[String, AnyRef]     = Map.empty): TkKafkaProducerLifecycle[F, K, V]
  def allocateConsumer[K: KafkaDeserializer, V: KafkaDeserializer](propsOverride: Map[String, AnyRef] = Map.empty): TkKafkaConsumerLifecycle[F, K, V]
}

object KafkaClients {
  final class Impl[F[+_, +_]: Async2: BlockingIO2](
    kafkaConf: KafkaPropsConfig
  ) extends KafkaClients[F] {
    override def allocateProducer[K: KafkaSerializer, V: KafkaSerializer](propsOverride: Map[String, AnyRef]): TkKafkaProducerLifecycle[F, K, V] = {
      TkKafkaProducer.allocate(kafkaConf.getProducerProps ++ propsOverride)
    }
    override def allocateConsumer[K: KafkaDeserializer, V: KafkaDeserializer](propsOverride: Map[String, AnyRef]): TkKafkaConsumerLifecycle[F, K, V] = {
      TkKafkaConsumer.allocate(kafkaConf.getConsumerProps ++ propsOverride)
    }
  }
}
