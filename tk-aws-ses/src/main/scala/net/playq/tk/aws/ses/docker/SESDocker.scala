package net.playq.tk.aws.ses.docker

import izumi.distage.docker.Docker.AvailablePort
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.aws.docker.LocalStackDocker
import net.playq.tk.aws.ses.config.SESConfig

object SESDocker extends LocalStackDocker[SESConfig] {
  override def rewriteConfig(conf: SESConfig)(ports: NEList[AvailablePort]): SESConfig = {
    val port = ports.head
    conf.copy(
      endpointUrl = Some(s"http://${port.host.host}:${port.port}")
    )
  }
}
