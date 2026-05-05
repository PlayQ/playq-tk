package net.playq.tk.zookeeper.docker

import izumi.distage.docker.Docker
import izumi.distage.docker.Docker.DockerPort
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.docker.TkContainerDef
import net.playq.tk.zookeeper.config.ZookeeperConfig

object ZookeeperDocker extends TkContainerDef[ZookeeperConfig] {
  override def registry: Option[String]         = Some("public.ecr.aws")
  override def image: String                    = "docker/library/zookeeper:3.6.4"
  override def containerPort: Docker.DockerPort = DockerPort.TCP(2181)

  override protected def rewriteConfig(conf: ZookeeperConfig)(ports: NEList[Docker.AvailablePort]): ZookeeperConfig = {
    val connectionString = ports.map(local => s"${local.host.host}:${local.port}").mkString(",")
    ZookeeperConfig(connectionString)
  }
}
