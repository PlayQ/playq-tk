package net.playq.tk.plugins

import distage.TagKK
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginDef
import net.playq.tk.rocksdb.{RocksBaseConfig, RocksBaseFactory}
import zio.IO

object RocksDBPlugin extends PluginDef {
  include(prod[IO])
  include(dummy[IO])
  include(config)

  def prod[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Prod)

    make[RocksBaseFactory[F]].from[RocksBaseFactory.Impl[F]]
  }

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Dummy)

    make[RocksBaseFactory[F]].from[RocksBaseFactory.Dummy[F]]
  }

  object config extends ConfigModuleDef {
    makeConfig[RocksBaseConfig]("rocksdb")
  }
}
