package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import distage.plugins.PluginDef
import distage.{Mode, ModuleDef}
import io.circe.Printer
import izumi.idealingua.runtime.rpc.{IRTClientMultiplexor, IRTClientMultiplexorImpl}
import izumi.reflect.TagKK
import net.playq.tk.http.TkHttp4sClient.HttpClientCfg
import net.playq.tk.http.config.HttpInterfaceConfig
import net.playq.tk.http.{TkHttp4sClient, TkHttpInterface}
import zio.IO

import java.net.InetAddress

object TkBasicHttpPlugin extends PluginDef {
  include(module[IO])
  include(config)

  def module[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[TkHttp4sClient[F]].from[TkHttp4sClient.Impl[F]]

    make[Printer].fromValue(Printer.noSpaces.copy(dropNullValues = true))
    make[IRTClientMultiplexor[F]].from[IRTClientMultiplexorImpl[F]]

    // interface binding
    make[TkHttpInterface].tagged(Mode.Prod).fromResource[TkHttpInterface.Resource[F]]
    make[TkHttpInterface].tagged(Mode.Test).from(TkHttpInterface.Static("0.0.0.0", InetAddress.getByName("0.0.0.0"), 8080))
  }

  object config extends ConfigModuleDef {
    makeConfig[HttpInterfaceConfig]("http")
    makeConfig[HttpClientCfg]("httpClient")
  }
}
