package net.playq.tk.redis.docker

import distage.config.{ConfigModuleDef, DIConfigReader}
import distage.{Scene, TagK}
import izumi.distage.docker.Docker
import izumi.distage.docker.Docker.DockerPort
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.docker.{TkContainerDef, TkDefaultNetwork}
import net.playq.tk.redis.config.RedisConfig
import net.playq.tk.redis.config.RedisConfig.{RedisAuthMode, RedisServerMode}

// todo:
//  not working as ports in docker container should be aligned with outside of container for discovery to work properly
//  use docker compose script in /docker directory if needed to test cluster mode
object RedisClusterDocker extends TkContainerDef[RedisConfig] {
  // unused
  override def containerPort: Docker.DockerPort = null

  override protected def rewriteConfig(conf: RedisConfig)(ports: NEList[Docker.AvailablePort]): RedisConfig = conf
  override def registry: Option[String]                                                                     = None
  override def image: String                                                                                = "grokzen/redis-cluster:7.0.0"

  override def config: RedisClusterDocker.Config = super.config.copy(
    ports = Seq(
      DockerPort.TCP(7000),
      DockerPort.TCP(7001),
      DockerPort.TCP(7002),
      DockerPort.TCP(7003),
      DockerPort.TCP(7004),
      DockerPort.TCP(7005),
    ),
    env = Map(
      "IP" -> "0.0.0.0"
    ),
  )

  override protected def testModule[F[_]: TagK](
    path: String
  )(implicit
    dec: DIConfigReader[RedisConfig],
    tc: distage.Tag[RedisClusterDocker.Container],
    tt: distage.Tag[RedisClusterDocker.Tag],
  ): distage.Module = {
    new ConfigModuleDef {
      tag(Scene.Managed)

      make[RedisClusterDocker.Container].fromResource(RedisClusterDocker.make[F].connectToNetwork(TkDefaultNetwork))
      make[RedisConfig].from {
        wireConfig[RedisConfig](path).flatAp {
          (c: RedisClusterDocker.Container) => (_: RedisConfig) =>
            val endpoints = config.ports.map(c.availablePorts.first).map(p => s"http://${p.host.host}:${p.port}")
            RedisConfig(
              endpoint              = endpoints.mkString(","),
              integrationCheck      = true,
              serverMode            = RedisServerMode.ClusterMode,
              authMode              = RedisAuthMode.NoAuthMode,
              user                  = None,
              password              = None,
              awsUserId             = None,
              awsReplicationGroupId = None,
              awsRegion             = None,
            )
        }
      }
    }
  }

}
