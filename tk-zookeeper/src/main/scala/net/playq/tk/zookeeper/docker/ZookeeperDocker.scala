package net.playq.tk.zookeeper.docker

import izumi.distage.docker.Docker
import izumi.distage.docker.Docker.DockerPort
import izumi.fundamentals.collections.nonempty.NonEmptyList
import net.playq.tk.docker.TkContainerDef
import net.playq.tk.zookeeper.config.ZookeeperConfig

object ZookeeperDocker extends TkContainerDef[ZookeeperConfig] {
  override def image: String                    = "zookeeper:3.4.14"
  override def containerPort: Docker.DockerPort = DockerPort.TCP(2181)

  override protected def rewriteConfig(conf: ZookeeperConfig)(ports: NonEmptyList[Docker.AvailablePort]): ZookeeperConfig = {
    val connectionString = ports.map(local => s"${local.host.host}:${local.port}").mkString(",")
    ZookeeperConfig(connectionString)
  }
}
