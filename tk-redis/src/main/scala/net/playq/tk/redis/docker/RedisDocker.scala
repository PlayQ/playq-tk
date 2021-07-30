package net.playq.tk.redis.docker

import izumi.distage.docker.Docker
import izumi.distage.docker.Docker.DockerPort
import izumi.fundamentals.collections.nonempty.NonEmptyList
import net.playq.tk.docker.TkContainerDef
import net.playq.tk.redis.config.RedisConfig

object RedisDocker extends TkContainerDef[RedisConfig] {
  override def containerPort: Docker.DockerPort = DockerPort.TCP(6379)

  override def image: String = "redis:latest"

  override protected def rewriteConfig(conf: RedisConfig)(ports: NonEmptyList[Docker.AvailablePort]): RedisConfig = {
    val port = ports.head
    RedisConfig(endpoint = s"http://${port.host.host}:${port.port}", integrationCheck = true)
  }
}
