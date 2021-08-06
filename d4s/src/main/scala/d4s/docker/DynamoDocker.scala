package d4s.docker

import d4s.config.DynamoConfig
import izumi.distage.docker.Docker.{AvailablePort, DockerPort}
import izumi.fundamentals.collections.nonempty.NonEmptyList
import net.playq.tk.docker.TkContainerDef

object DynamoDocker extends TkContainerDef[DynamoConfig] {
  override def image: String             = "amazon/dynamodb-local:latest"
  override def containerPort: DockerPort = DockerPort.TCP(8000)
  override def rewriteConfig(conf: DynamoConfig)(ports: NonEmptyList[AvailablePort]): DynamoConfig = {
    val port = ports.head
    conf.copy(
      endpointUrl = Some(s"http://${port.host.host}:${port.port}"),
      region      = conf.getRegion.orElse(Some("us-east-1")),
    )
  }
}
