package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import distage.{ModuleDef, Repo, TagKK}
import net.playq.tk.synchro.{TkSynchronizer, TkSynchronizerZookeeper}
import zio.IO

object TkSynchronizerZkPlugin extends PluginDef {
  include(TkSynchronizerZkPlugin.zk[IO])

  def zk[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Prod)
    make[TkSynchronizer[F]].named("zookeeper").from[TkSynchronizerZookeeper[F]]
  }
}
