package net.playq.tk.plugins

import distage.{ModuleDef, Repo}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import izumi.reflect.TagKK
import net.playq.tk.aws.lambda.{LambdaClientFactory, LambdaComponent}
import net.playq.tk.aws.lambda.config.LambdaConfig
import zio.IO

object LambdaPlugins extends PluginDef {
  include(LambdaPlugins.prod[IO])
  include(LambdaPlugins.dummy[IO])

  def prod[F[+_, +_]: TagKK]: ModuleDef = new ConfigModuleDef {
    tag(Repo.Prod)

    make[LambdaComponent[F]].fromResource[LambdaComponent.Resource[F]]
    make[LambdaClientFactory[F]].from[LambdaClientFactory.Impl[F]]
    makeConfig[LambdaConfig]("aws.lambda")
  }

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ConfigModuleDef {
    tag(Repo.Dummy)

    make[LambdaClientFactory[F]].from[LambdaClientFactory.Dummy[F]]
  }
}
