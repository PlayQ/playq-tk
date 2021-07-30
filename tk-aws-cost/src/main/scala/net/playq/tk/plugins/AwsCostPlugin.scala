package net.playq.tk.plugins

import distage.TagKK
import distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.cost.{CostClient, CostComponent}
import net.playq.tk.aws.cost.config.CostConfig
import zio.IO

object AwsCostPlugin extends PluginDef {
  include(AwsCostPlugin.module[IO])

  def module[F[+_, +_]: TagKK]: ConfigModuleDef = new ConfigModuleDef {
    make[CostComponent[F]].from[CostComponent.Impl[F]]
    make[CostClient[F]].fromResource((_: CostComponent[F]).resourceClient)
    makeConfig[CostConfig]("aws.cost")
  }
}
