package net.playq.tk.plugins

import distage.{ModuleDef, TagKK}
import izumi.distage.plugins.PluginDef
import net.playq.tk.compression.TkCompression
import zio.IO

object TkCompressionPlugin extends PluginDef {
  include(TkCompressionPlugin.module[IO])

  def module[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[TkCompression[F]].from[TkCompression.WithZstd[F]]
  }
}
