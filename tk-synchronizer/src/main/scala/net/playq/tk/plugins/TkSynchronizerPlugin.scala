package net.playq.tk.plugins

import distage.{ModuleDef, Repo, TagKK}
import izumi.distage.plugins.PluginDef
import net.playq.tk.synchro.TkSynchronizer
import zio.IO

object TkSynchronizerPlugin extends PluginDef {
  include(TkSynchronizerPlugin.dummy[IO])

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Dummy)
    make[TkSynchronizer[F]].named("zookeeper").fromResource[TkSynchronizer.Dummy[F]]
  }
}
