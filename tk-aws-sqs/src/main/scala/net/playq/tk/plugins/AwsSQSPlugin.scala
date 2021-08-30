package net.playq.tk.plugins

import distage.{TagK, TagKK}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.sqs.docker.SQSDocker
import net.playq.tk.aws.sqs.*
import net.playq.tk.health.HealthChecker
import zio.IO

object AwsSQSPlugin extends PluginDef {
  include(module[IO])
  many[SQSQueueId]

  def module[F[+_, +_]: TagKK](implicit T: TagK[F[Throwable, _]]): ConfigModuleDef = new ConfigModuleDef {
    make[SQSComponent[F]].fromResource((_: SQSComponentFactory[F]).mkClient(None))
    make[SQSComponentFactory[F]].from[SQSComponentFactory.Impl[F]]

    make[SQSQueuesManager[F]].from[SQSQueuesManager.SQSQueuesManagerImpl[F]]
    make[SQSHealthChecker[F]]
    many[HealthChecker[F]].weak[SQSHealthChecker[F]]

    include(SQSDocker.module[F[Throwable, _]]("aws.sqs"))
  }
}
