package net.playq.tk.kafka.docker

import distage.{Module, ModuleDef, TagK}
import izumi.distage.config.codec.DIConfigReader
import izumi.distage.docker.Docker
import izumi.distage.docker.Docker.DockerPort
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.fundamentals.collections.nonempty.NonEmptyList
import net.playq.tk.docker.{TkContainerDef, TkDefaultNetwork}
import net.playq.tk.kafka.config.KafkaPropsConfig
import net.playq.tk.zookeeper.docker.ZookeeperDocker

object KafkaDocker extends TkContainerDef[KafkaPropsConfig] {
  override def image: String             = "wurstmeister/kafka:1.0.0"
  override def containerPort: DockerPort = DockerPort.DynamicTCP("dynamic_kafka_port")

  private[this] def portVars: String = Seq(
    s"""KAFKA_ADVERTISED_PORT=$$${containerPort.toEnvVariable}""",
    s"""KAFKA_PORT=$$${containerPort.toEnvVariable}""",
  ).map(defn => s"export $defn").mkString("; ")

  override protected def rewriteConfig(conf: KafkaPropsConfig)(ports: NonEmptyList[Docker.AvailablePort]): KafkaPropsConfig = {
    conf.copy(bootstrapServers = ports.map(p => s"${p.host.host}:${p.port}").mkString(","))
  }

  override def config: Config = super.config.copy(
    env        = Map("KAFKA_ADVERTISED_HOST_NAME" -> "127.0.0.1"),
    entrypoint = Seq("sh", "-c", s"$portVars ; start-kafka.sh"),
  )

  override protected def testModule[F[_]: TagK](
    path: String
  )(implicit dec: DIConfigReader[KafkaPropsConfig],
    tc: distage.Tag[KafkaDocker.Container],
    tt: distage.Tag[KafkaDocker.Tag],
  ): Module =
    super.testModule(path) overriddenBy new ModuleDef {
      tag(Scene.Managed)

      make[KafkaDocker.Container].fromResource {
        KafkaDocker
          .make[F]
          .connectToNetwork(TkDefaultNetwork)
          .modifyConfig {
            zookeeperDocker: ZookeeperDocker.Container => old: KafkaDocker.Config =>
              val zkEnv = old.env ++ Map("KAFKA_ZOOKEEPER_CONNECT" -> s"${zookeeperDocker.hostName}:2181")
              old.copy(env = zkEnv)
          }
      }
    }

}
