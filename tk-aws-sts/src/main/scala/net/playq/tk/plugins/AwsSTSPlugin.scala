package net.playq.tk.plugins

import distage.TagKK
import distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.sts.STSComponent
import net.playq.tk.aws.sts.config.STSConfig
import zio.IO

object AwsSTSPlugin extends PluginDef {
  include(AwsSTSPlugin.module[IO])

  def module[F[+_, +_]: TagKK]: ModuleDef = new ConfigModuleDef {
    make[STSComponent[F]].from[STSComponent.Impl[F]]
    makeConfig[STSConfig]("aws.sts")
  }
}
