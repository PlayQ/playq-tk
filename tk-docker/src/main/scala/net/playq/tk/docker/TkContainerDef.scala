package net.playq.tk.docker

import distage.{Module, ModuleDef, TagK}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.config.codec.DIConfigReader
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.Docker.{AvailablePort, DockerPort}
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.fundamentals.collections.nonempty.NonEmptyList

abstract class TkContainerDef[ServiceConfig: distage.Tag] extends ContainerDef {
  self: Singleton =>

  def containerPort: DockerPort
  def image: String
  protected def rewriteConfig(conf: ServiceConfig)(ports: NonEmptyList[AvailablePort]): ServiceConfig

  override def config: Config = Config(
    image = image,
    ports = Seq(containerPort),
  )

  final def module[F[_]: TagK](confPath: String)(implicit dec: DIConfigReader[ServiceConfig], tc: distage.Tag[self.Container], tt: distage.Tag[self.Tag]): Module = {
    new ModuleDef {
      include(testModule[F](confPath))
      include(prodModule(confPath))
    }
  }

  protected[this] def testModule[F[_]: TagK](
    path: String
  )(implicit dec: DIConfigReader[ServiceConfig],
    tc: distage.Tag[self.Container],
    tt: distage.Tag[self.Tag],
  ): Module = {
    new ConfigModuleDef {
      tag(Scene.Managed)

      make[self.Container].fromResource(self.make[F].connectToNetwork(TkDefaultNetwork))
      make[ServiceConfig].from {
        wireConfig[ServiceConfig](path).flatAp {
          container: self.Container => cfg: ServiceConfig =>
            val ep = container.availablePorts.get(containerPort)
            ep.fold(cfg)(rewriteConfig(cfg))
        }
      }
    }
  }

  protected def prodModule(path: String)(implicit dec: DIConfigReader[ServiceConfig]): Module = {
    new ConfigModuleDef {
      tag(Scene.Provided)

      makeConfig[ServiceConfig](path)
    }
  }
}
