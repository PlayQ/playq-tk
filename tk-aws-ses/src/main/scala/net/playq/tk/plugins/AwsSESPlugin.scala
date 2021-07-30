package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import distage.{ModuleDef, TagK, TagKK}
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.ses.docker.SESDocker
import net.playq.tk.aws.ses.{SESClient, SESComponent, SESResource}
import zio.IO

object AwsSESPlugin extends PluginDef {
  include(AwsSESPluginBase.prod[IO])
  include(AwsSESPluginBase.dummy[IO])
}

object AwsSESPluginBase {
  def prod[F[+_, +_]: TagKK](implicit T: TagK[F[Throwable, ?]]): ConfigModuleDef = new ConfigModuleDef {
    tag(Repo.Prod)
    make[SESComponent[F]].fromResource[SESResource[F]]
    make[SESClient[F]].from[SESClient.Impl[F]]
    include(SESDocker.module[F[Throwable, ?]]("aws.ses"))
  }

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Dummy)
    make[SESClient[F]].from[SESClient.Dummy[F]]
  }
}
