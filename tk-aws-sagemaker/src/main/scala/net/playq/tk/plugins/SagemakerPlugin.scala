package net.playq.tk.plugins

import distage.ModuleDef
import distage.config.ConfigModuleDef
import distage.plugins.PluginDef
import izumi.reflect.TagKK
import net.playq.tk.aws.sagemaker.config.{SagemakerConfig, TrainingImageConfig}
import net.playq.tk.aws.sagemaker.{SagemakerClient, TrainingImageProvider}
import zio.IO

object SagemakerPlugin extends PluginDef {
  include(services[IO])
  include(config)

  private[this] def services[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[SagemakerClient[F]].fromResource[SagemakerClient.Impl[F]]
    make[TrainingImageProvider[F]]
  }

  private[this] def config: ModuleDef = new ConfigModuleDef {
    makeConfig[SagemakerConfig]("aws.sagemaker")
    makeConfig[TrainingImageConfig]("aws.sagemaker")
  }
}
