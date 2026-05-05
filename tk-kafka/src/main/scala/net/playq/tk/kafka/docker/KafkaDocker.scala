package net.playq.tk.kafka.docker

import izumi.distage.docker.Docker
import izumi.distage.docker.Docker.DockerPort
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.docker.TkContainerDef
import net.playq.tk.kafka.config.KafkaPropsConfig

object KafkaDocker extends TkContainerDef[KafkaPropsConfig] {
  override def registry: Option[String]  = None // aws ecr missing amd64 image for bitnami-kafka
  override def image: String             = "bitnamilegacy/kafka:3.4.0"
  override def containerPort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port")

  override protected def rewriteConfig(conf: KafkaPropsConfig)(ports: NEList[Docker.AvailablePort]): KafkaPropsConfig = {
    conf.copy(bootstrapServers = ports.map(p => s"${p.host.host}:${p.port}").mkString(","))
  }

  override def config: Config = super.config.copy(
    env = {
      portVars =>
        val port = portVars(containerPort)
        Map(
          "ALLOW_PLAINTEXT_LISTENER"                 -> "yes",
          "KAFKA_ENABLE_KRAFT"                       -> "yes",
          "KAFKA_BROKER_ID"                          -> "1",
          "KAFKA_CFG_ADVERTISED_HOST_NAME"           -> "127.0.0.1",
          "KAFKA_CFG_PROCESS_ROLES"                  -> "broker,controller",
          "KAFKA_CFG_CONTROLLER_LISTENER_NAMES"      -> "CONTROLLER",
          "KAFKA_CFG_CONTROLLER_QUORUM_VOTERS"       -> "1@127.0.0.1:9093",
          "KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP" -> "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
          "KAFKA_CFG_DELETE_TOPIC_ENABLE"            -> "true",
          "KAFKA_CFG_NODE_ID"                        -> "1",
          s"KAFKA_CFG_PORT"                          -> port,
          s"KAFKA_CFG_ADVERTISED_PORT"               -> port,
          s"KAFKA_CFG_LISTENERS"                     -> s"PLAINTEXT://:$port,CONTROLLER://:9093",
          s"KAFKA_CFG_ADVERTISED_LISTENERS"          -> s"PLAINTEXT://127.0.0.1:$port",
        )
    }
  )
}
