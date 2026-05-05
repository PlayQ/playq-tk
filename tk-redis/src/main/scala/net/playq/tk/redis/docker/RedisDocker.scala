package net.playq.tk.redis.docker

import izumi.distage.docker.Docker
import izumi.distage.docker.Docker.DockerPort
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.docker.TkContainerDef
import net.playq.tk.redis.config.RedisConfig
import net.playq.tk.redis.config.RedisConfig.{RedisAuthMode, RedisServerMode}

object RedisDocker extends TkContainerDef[RedisConfig] {
  val user: String                              = "user"
  val password: String                          = "password"
  override def containerPort: Docker.DockerPort = DockerPort.TCP(6379)

  override def registry: Option[String] = Some("public.ecr.aws")

  override def image: String = "docker/library/redis:7.0"

  override def config: Config = {
    super.config.copy(
      cmd = super.config.cmd ++ List(s"--requirepass $password", s"--user $user on ~* &* +@all >$password")
    )
  }

  override protected def rewriteConfig(conf: RedisConfig)(ports: NEList[Docker.AvailablePort]): RedisConfig = {
    val port = ports.head
    RedisConfig(
      endpoint              = s"redis://${port.host.host}:${port.port}",
      integrationCheck      = true,
      serverMode            = RedisServerMode.SingleMode,
      authMode              = RedisAuthMode.PassAuthMode,
      user                  = Some(user),
      password              = Some(password),
      awsUserId             = None,
      awsReplicationGroupId = None,
      awsRegion             = None,
    )
  }
}
