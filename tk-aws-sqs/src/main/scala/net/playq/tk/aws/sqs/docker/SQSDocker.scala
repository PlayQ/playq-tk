package net.playq.tk.aws.sqs.docker

import izumi.distage.docker.Docker.{AvailablePort, DockerPort}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.fundamentals.collections.nonempty.NonEmptyList
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.docker.TkContainerDef

object SQSDocker extends TkContainerDef[SQSConfig] {
  override def image: String             = "softwaremill/elasticmq:0.14.6"
  override def containerPort: DockerPort = DockerPort.TCP(9324)

  override def rewriteConfig(conf: SQSConfig)(ports: NonEmptyList[AvailablePort]): SQSConfig = {
    val port = ports.head
    conf.copy(
      endpoint = Some(s"http://${port.host.host}:${port.port}"),
      region   = conf.getRegion.orElse(Some("us-east-1")),
    )
  }

  override def config: SQSDocker.Config = {
    super.config.copy(
      entrypoint  = Seq("sh", "-c", "/opt/docker/bin/elasticmq-server -Dnode-address.port=$DISTAGE_PORT_TCP_9324"),
      healthCheck = ContainerHealthCheck.httpGetCheck(containerPort),
    )
  }
}
