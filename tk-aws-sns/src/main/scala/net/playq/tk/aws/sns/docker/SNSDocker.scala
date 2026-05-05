package net.playq.tk.aws.sns.docker
import izumi.distage.docker.model.Docker.AvailablePort
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.aws.docker.LocalStackDocker
import net.playq.tk.aws.sns.config.SNSConfig

object SNSDocker extends LocalStackDocker[SNSConfig] {
  override def rewriteConfig(conf: SNSConfig)(ports: NEList[AvailablePort]): SNSConfig = {
    val port = ports.head
    conf.copy(
      endpoint = Some(s"http://${port.host.host}:${port.port}")
    )
  }
}
