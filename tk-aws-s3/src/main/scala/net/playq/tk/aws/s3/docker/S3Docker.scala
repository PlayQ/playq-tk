package net.playq.tk.aws.s3.docker

import izumi.distage.docker.Docker.AvailablePort
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.aws.docker.LocalStackDocker
import net.playq.tk.aws.s3.config.S3Config

object S3Docker extends LocalStackDocker[S3Config] {
  override def rewriteConfig(conf: S3Config)(ports: NEList[AvailablePort]): S3Config = {
    val port = ports.head
    conf.copy(
      url = Some(s"http://${port.host.host}:${port.port}")
    )
  }
}
