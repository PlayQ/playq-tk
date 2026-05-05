package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import distage.{Mode, ModuleDef, TagK}
import io.circe.Printer
import izumi.distage.model.definition.Id
import izumi.distage.plugins.PluginDef
import izumi.idealingua.runtime.rpc.http4s.HttpExecutionContext
import izumi.idealingua.runtime.rpc.{IRTClientMultiplexor, IRTClientMultiplexorImpl, IRTWrappedClient}
import izumi.reflect.TagKK
import net.playq.tk.http.TkHttp4sClient.HttpClientCfg
import net.playq.tk.http.TkHttp4sServerResource.TkHttp4sServer
import net.playq.tk.http.config.HttpInterfaceConfig.HttpPortRange
import net.playq.tk.http.config.{HttpInterfaceConfig, HttpSecretConfig, TkIdlHttpServiceConfig}
import net.playq.tk.http.{TkHttp4sClient, TkHttp4sServerResource, TkHttpServerBindTarget, TkHttpServerBindTargetProvider}
import zio.IO

import scala.concurrent.ExecutionContext

object TkBasicHttpPlugin extends PluginDef {
  include(module[IO])
  include(config)

  def module[F[+_, +_]: TagKK](implicit monoTag: TagK[F[Throwable, _]]): ModuleDef = new ModuleDef {
    // http server
    make[TkHttp4sServer].fromResource[TkHttp4sServerResource[F]]

    // http server deps
    make[HttpExecutionContext].from {
      (blockingIOExecutionContext: ExecutionContext @Id("blockingIO")) =>
        HttpExecutionContext(blockingIOExecutionContext)
    }
    make[Printer].fromValue(Printer.noSpaces.copy(dropNullValues = true))

    // irt client codecs
    many[IRTWrappedClient]
    make[IRTClientMultiplexor[F]].from[IRTClientMultiplexorImpl[F]]

    // http client
    make[TkHttp4sClient[F]].fromResource[TkHttp4sClient.MkImpl[F]]

    // http interface binding
    make[TkHttpServerBindTargetProvider.Resource[F]]
    make[TkHttpServerBindTargetProvider].refResource[TkHttpServerBindTargetProvider.Resource[F]]
    make[TkHttpServerBindTarget].from {
      (provider: TkHttpServerBindTargetProvider) =>
        provider.unsafeGet
    }
  }

  object config extends ConfigModuleDef {
    makeConfig[HttpClientCfg]("http.client")
    makeConfig[HttpSecretConfig]("http.auth.private")
    makeConfig[HttpInterfaceConfig]("http").tagged(Mode.Prod)

    makeConfig[TkIdlHttpServiceConfig]("http")

    make[HttpInterfaceConfig].tagged(Mode.Test).fromValue {
      HttpInterfaceConfig(
        defaultInterfaceAddressOverride = Some("127.0.0.1"),
        host                            = "127.0.0.1",
        portRange                       = HttpPortRange(9000, 9999),
      )
    }

  }
}
