package net.playq.tk.plugins

import distage.plugins.PluginDef
import distage.{ModuleDef, TagKK}
import net.playq.tk.tmp.{CompressionIO2, CompressionIO2Gzip}
import zio.IO

object CompressionIO2Plugin extends PluginDef {
  include(CompressionIO2Plugin.module[IO])

  def module[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[CompressionIO2[F]].from[CompressionIO2Gzip[F]]
  }
}
