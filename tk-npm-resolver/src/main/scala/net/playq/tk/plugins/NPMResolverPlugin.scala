package net.playq.tk.plugins

import distage.TagKK
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginDef
import net.playq.tk.npmresolver.NPMLibsCache
import zio.IO

object NPMResolverPlugin extends PluginDef {
  include(dummy[IO])
//  include(config)

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Dummy)

    make[NPMLibsCache[F]].from[NPMLibsCache.Dummy[F]]
  }

//  def config: ConfigModuleDef = new ConfigModuleDef {
//    makeConfig[NPMResolverConfig]("npm")
//  }
}
