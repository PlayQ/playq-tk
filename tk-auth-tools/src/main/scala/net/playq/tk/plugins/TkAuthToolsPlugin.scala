package net.playq.tk.plugins

import distage.plugins.PluginDef
import distage.{ModuleDef, TagKK}
import net.playq.tk.authtolls.{AppleIdAuth, GoogleAuthorizationService, GoogleOAuth, OTPTools}
import zio.IO

object TkAuthToolsPlugin extends PluginDef {
  include(TkAuthToolsPlugin.module[IO])

  def module[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[AppleIdAuth[F]]
    make[GoogleOAuth[F]]
    make[GoogleAuthorizationService[F]].from[GoogleAuthorizationService.Impl[F]]
    make[OTPTools[F]].from[OTPTools.Impl[F]]
  }
}
