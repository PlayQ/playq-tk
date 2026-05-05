package net.playq.tk.plugins

import distage.TagKK
import distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.PluginDef
import net.playq.tk.http.TkHttp4sService
import net.playq.tk.http.ws.WsInfraClient.WsClientPoolConfig
import net.playq.tk.http.ws.{WsInfraClient, WsInfraConnector, WsInfraMessageHandler, WsInfraServer}
import zio.IO

object WsClientServerPlugin extends PluginDef {
  include(clientServer[IO])

  def clientServer[F[+_, +_]: TagKK]: ModuleDef = new ConfigModuleDef {

    make[WsInfraConnector[F]].fromResource[WsInfraConnector.Resource[F]]
    make[WsInfraClient[F]].fromResource[WsInfraClient.Resource[F]]

    make[WsInfraServer[F]]
    many[TkHttp4sService[F]].weak[WsInfraServer[F]]

    many[WsInfraMessageHandler[F, ?, ?]]

    makeConfig[WsClientPoolConfig]("ws.client")
  }
}
