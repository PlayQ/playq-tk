package net.playq.tk.aws.ses.docker

import izumi.distage.docker.Docker.{AvailablePort, DockerPort}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.fundamentals.collections.nonempty.NonEmptyList
import net.playq.tk.aws.ses.config.SESConfig
import net.playq.tk.docker.TkContainerDef

object SESDocker extends TkContainerDef[SESConfig] {
  override def image: String             = "localstack/localstack:0.11.4"
  override def containerPort: DockerPort = DockerPort.TCP(4579)

  override def rewriteConfig(conf: SESConfig)(ports: NonEmptyList[AvailablePort]): SESConfig = {
    val port = ports.head
    conf.copy(
      endpointUrl = Some(s"http://${port.host.host}:${port.port}"),
      region      = conf.getRegion.orElse(Some("us-east-1")),
    )
  }

  override def config: Config = super.config.copy(
    env = Map(
      "SERVICES" -> "ses:4579",
      "DEBUG"    -> s"$${DEBUG- }",
    ),
    healthCheck = ContainerHealthCheck.httpGetCheck(containerPort),
  )
}
