package net.playq.tk.plugins

import distage.plugins.PluginDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.reflect.TagKK
import net.playq.tk.zookeeper.SynchronizedAction
import zio.IO

object SynchronizedLoopPlugin extends PluginDef {
  include(SynchronizedLoopPlugin.prod[IO])
  include(SynchronizedLoopPlugin.dummy[IO])

  def prod[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Prod)
    make[SynchronizedAction[F]].fromResource[SynchronizedAction.Zookeeper[F]]
  }

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Dummy)
    make[SynchronizedAction[F]].fromResource[SynchronizedAction.Dummy[F]]
  }
}
