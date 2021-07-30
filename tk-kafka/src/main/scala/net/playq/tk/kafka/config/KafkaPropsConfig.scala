package net.playq.tk.kafka.config

import com.typesafe.config.ConfigValue
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig

final case class KafkaPropsConfig(
  bootstrapServers: String,
  private val consumer: Option[Map[String, ConfigValue]],
  private val producer: Option[Map[String, ConfigValue]],
) {

  def getConsumerProps: Map[String, AnyRef] =
    toKafkaProps(consumer) ++ Map(
      ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> s"$bootstrapServers"
    )

  def getProducerProps: Map[String, AnyRef] =
    toKafkaProps(producer) ++ Map(
      ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> s"$bootstrapServers"
    )

  /**
    * replace _ with . in props; workaround for a lack of custom codec for kafka props
    *
    * FIXME: use a custom [[distage.config.ConfigReader]] for nicer syntax
    */
  private[this] def toKafkaProps(maybeProps: Option[Map[String, ConfigValue]]): Map[String, String] =
    maybeProps.map(_.map { case (k, v) => k.replace('_', '.') -> v.unwrapped().toString }) getOrElse Map.empty
}
