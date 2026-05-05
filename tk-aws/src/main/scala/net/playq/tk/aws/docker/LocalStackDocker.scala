package net.playq.tk.aws.docker

import izumi.distage.docker.Docker.DockerPort
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import net.playq.tk.docker.TkContainerDef

abstract class LocalStackDocker[ServiceConfig: distage.Tag] extends TkContainerDef[ServiceConfig] {
  self: Singleton =>

  final override def registry: Option[String]  = Some("public.ecr.aws")
  final override def image: String             = "localstack/localstack:4.0.3"
  final override def containerPort: DockerPort = DockerPort.TCP(4566)
  final override def config: this.Config       = super.config.copy(
    healthCheck = ContainerHealthCheck.httpGetCheck(containerPort),
    env         = Map(
      "DEBUG" -> "0"
    ),
  )
}
