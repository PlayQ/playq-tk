package net.playq.tk.plugins

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import distage.config.ConfigModuleDef
import distage.{ModuleDef, TagK}
import izumi.distage.docker.Docker
import izumi.distage.docker.impl.DockerClientWrapper.DockerIntegrationCheck
import izumi.distage.docker.impl.{DockerClientFactory, DockerClientWrapper}
import izumi.functional.Value
import izumi.functional.lifecycle.Lifecycle
import net.playq.tk.docker.TkDefaultNetwork

final class TkDockerModule[F[_]: TagK] extends ModuleDef {
  include(TkDockerModule.module[F])
  include(TkDockerModule.config)
}

object TkDockerModule {
  def apply[F[_]: TagK] = new TkDockerModule[F]

  final val config = new ConfigModuleDef {
    makeConfigWithDefault[Docker.ClientConfig]("docker") {
      val registry = sys.env.get("TK_DOCKER_GLOBAL_REGISTRY")
      Docker.ClientConfig(globalRegistry = registry, useGlobalRegistry = registry.nonEmpty)
    }
    make[DefaultDockerClientConfig].from {
      (clientConfig: Docker.ClientConfig) =>
        /** We do not need to use global registry here since it would be overridden in CMD requests. */
        val remote = clientConfig.remote.filter(_ => clientConfig.useRemote)
        Value(DefaultDockerClientConfig.createDefaultConfigBuilder())
          .mut(remote)((b, c) => b.withDockerHost(c.host).withDockerTlsVerify(c.tlsVerify).withDockerCertPath(c.certPath).withDockerConfig(c.config))
          .get.build()
    }
  }

  def module[F[_]: TagK]: ModuleDef = new ModuleDef {
    make[DockerClient].fromResource {
      (clientConfig: Docker.ClientConfig, rawClientConfig: DefaultDockerClientConfig, clientFactory: DockerClientFactory) =>
        Lifecycle.fromAutoCloseable(clientFactory.makeClient(clientConfig, rawClientConfig))
    }
    make[DockerClientWrapper[F]].fromResource[DockerClientWrapper.Resource[F]]
    make[DockerClientFactory].from(DockerClientFactory.impl)
    make[DockerIntegrationCheck[F]]

    make[TkDefaultNetwork.Network].fromResource(TkDefaultNetwork.make[F])
  }
}
