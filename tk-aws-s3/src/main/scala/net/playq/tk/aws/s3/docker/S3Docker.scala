package net.playq.tk.aws.s3.docker

import izumi.distage.docker.Docker.{AvailablePort, DockerPort}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.fundamentals.collections.nonempty.NonEmptyList
import net.playq.tk.aws.s3.config.S3Config
import net.playq.tk.docker.TkContainerDef

object S3Docker extends TkContainerDef[S3Config] {
  override def image: String             = "adobe/s3mock:2.1.29"
  override def containerPort: DockerPort = DockerPort.TCP(9090)

  override def config: S3Docker.Config = super.config.copy(
    healthCheck = ContainerHealthCheck.httpGetCheck(containerPort)
  )

  override def rewriteConfig(conf: S3Config)(ports: NonEmptyList[AvailablePort]): S3Config = {
    val port = ports.head
    conf.copy(
      url    = Some(s"http://${port.host.host}:${port.port}"),
      region = conf.getRegion.orElse(Some("us-east-1")),
    )
  }
}
