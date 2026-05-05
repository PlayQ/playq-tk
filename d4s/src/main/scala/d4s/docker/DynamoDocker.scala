package d4s.docker

import d4s.config.DynamoConfig
import izumi.distage.docker.Docker.{AvailablePort, DockerPort}
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.docker.TkContainerDef

object DynamoDocker extends TkContainerDef[DynamoConfig] {
  override def registry: Option[String]                                                      = Some("public.ecr.aws")
  override def image: String                                                                 = "aws-dynamodb-local/aws-dynamodb-local:2.3.0"
  override def containerPort: DockerPort                                                     = DockerPort.TCP(8000)
  override def rewriteConfig(conf: DynamoConfig)(ports: NEList[AvailablePort]): DynamoConfig = {
    val port = ports.head
    conf.copy(
      endpointUrl = Some(s"http://${port.host.host}:${port.port}"),
      region      = conf.getRegion.orElse(Some("us-east-1")),
    )
  }
}
