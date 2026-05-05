package net.playq.tk.docker

import distage.{Module, TagK}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.config.codec.{DIConfigMeta, DIConfigReader}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.{AvailablePort, DockerPort}
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.fundamentals.collections.nonempty.NEList
import izumi.fundamentals.platform.strings.IzString.*

abstract class TkContainerDef[ServiceConfig: distage.Tag] extends ContainerDef {
  self: Singleton =>

  def containerPort: DockerPort
  def registry: Option[String]
  def image: String
  protected def rewriteConfig(conf: ServiceConfig)(ports: NEList[AvailablePort]): ServiceConfig

  override def config: Config = Config(
    registry = registry,
    image    = image,
    ports    = Seq(containerPort),
  )

  final def module[F[_]: TagK](
    confPath: String
  )(implicit
    reader: DIConfigReader[ServiceConfig],
    meta: DIConfigMeta[ServiceConfig],
    tc: distage.Tag[self.Container],
    tt: distage.Tag[self.Tag],
  ): Module = {
    new ConfigModuleDef {
      val disableDocker: Boolean = System.getProperty(s"tk.docker.$confPath.disable").asBoolean().getOrElse(false)
      if (disableDocker) {
        makeConfig[ServiceConfig](confPath)
      } else {
        include(prodModule(confPath))
        include(testModule[F](confPath))
      }
    }
  }

  protected def prodModule(
    path: String
  )(implicit
    reader: DIConfigReader[ServiceConfig],
    mate: DIConfigMeta[ServiceConfig],
  ): Module = {
    new ConfigModuleDef {
      tag(Scene.Provided)

      makeConfig[ServiceConfig](path)
    }
  }

  protected def testModule[F[_]: TagK](
    path: String
  )(implicit
    reader: DIConfigReader[ServiceConfig],
    tc: distage.Tag[self.Container],
    tt: distage.Tag[self.Tag],
  ): Module = {
    new ConfigModuleDef {
      tag(Scene.Managed)

      make[self.Container].fromResource(self.make[F].connectToNetwork(TkDefaultNetwork))
      make[ServiceConfig].from {
        wireConfig[ServiceConfig](path).flatAp {
          (container: self.Container) => (cfg: ServiceConfig) =>
            val ports = container.availablePorts.get(containerPort)
            ports.fold(cfg)(rewriteConfig(cfg))
        }
      }
    }
  }

}
