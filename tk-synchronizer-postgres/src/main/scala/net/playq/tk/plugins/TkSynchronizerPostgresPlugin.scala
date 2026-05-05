package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import distage.{ModuleDef, Repo, TagKK}
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.synchro.TkSynchronizerPostgres.MutexDDL
import net.playq.tk.synchro.config.PostgresSynchronizerConfig
import net.playq.tk.synchro.{TkSynchronizer, TkSynchronizerPostgres}
import zio.IO

object TkSynchronizerPostgresPlugin extends PluginDef {
  include(TkSynchronizerPostgresPlugin.postgres[IO])
  include(TkSynchronizerPostgresPlugin.dummy[IO])

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ConfigModuleDef {
    tag(Repo.Dummy)
    make[TkSynchronizer[F]].named("postgres").fromResource[TkSynchronizer.Dummy[F]]
  }

  def postgres[F[+_, +_]: TagKK]: ModuleDef = new ConfigModuleDef {
    tag(Repo.Prod)
    makeConfig[PostgresSynchronizerConfig]("postgres.synchronizer")
    make[TkSynchronizerPostgres[F]]
    make[TkSynchronizer[F]].named("postgres").using[TkSynchronizerPostgres[F]]
    DDLComponent.make[F, MutexDDL]
  }
}
