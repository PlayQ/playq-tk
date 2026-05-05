package net.playq.tk.plugins
import distage.{TagK, TagKK}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.sns.docker.SNSDocker
import net.playq.tk.aws.sns.{SNSComponent, SNSComponentFactory, SNSTopicsManager}
import zio.IO

object AwsSNSPlugin extends PluginDef {
  include(module[IO])

  def module[F[+_, +_]: TagKK](implicit T: TagK[F[Throwable, _]]): ConfigModuleDef = new ConfigModuleDef {
    make[SNSComponent[F]].fromResource((_: SNSComponentFactory[F]).mkComponent(None))
    make[SNSComponentFactory[F]].from[SNSComponentFactory.Impl[F]]

    make[SNSTopicsManager[F]].from[SNSTopicsManager.Impl[F]]

    include(SNSDocker.module[F[Throwable, _]]("aws.sns"))
  }
}
