package net.playq.tk.plugins

import distage.{TagK, TagKK}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.sqs.docker.SQSDocker
import net.playq.tk.aws.sqs.{SQSComponent, SQSComponentFactory, SQSHealthChecker, SQSQueuesManager}
import zio.IO

object AwsSQSPlugin extends PluginDef {
  include(module[IO])

  def module[F[+_, +_]: TagKK](implicit T: TagK[F[Throwable, ?]]): ConfigModuleDef = new ConfigModuleDef {
    make[SQSComponent[F]].fromResource((_: SQSComponentFactory[F]).mkClient(None))
    make[SQSComponentFactory[F]].from[SQSComponentFactory.Impl[F]]

    make[SQSQueuesManager[F]].from[SQSQueuesManager.SQSQueuesManagerImpl[F]]
    make[SQSHealthChecker[F]]

    include(SQSDocker.module[F[Throwable, ?]]("aws.sqs"))
  }
}
