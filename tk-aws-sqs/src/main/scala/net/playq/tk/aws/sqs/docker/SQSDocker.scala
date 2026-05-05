package net.playq.tk.aws.sqs.docker

import izumi.distage.docker.Docker.AvailablePort
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.aws.docker.LocalStackDocker
import net.playq.tk.aws.sqs.config.SQSConfig

object SQSDocker extends LocalStackDocker[SQSConfig] {
  override def rewriteConfig(conf: SQSConfig)(ports: NEList[AvailablePort]): SQSConfig = {
    val port = ports.head
    conf.copy(
      endpoint = Some(s"http://${port.host.host}:${port.port}")
    )
  }
}
