package net.playq.tk.plugins

import distage.{TagK, TagKK}
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.s3.docker.S3Docker
import net.playq.tk.aws.s3.health.S3HealthChecker
import net.playq.tk.aws.s3.{S3BucketCached, S3BucketFactory, S3Component, S3ComponentFactory}
import net.playq.tk.health.HealthChecker
import zio.IO

object AwsS3Plugin extends PluginDef with ConfigModuleDef {
  include(prod[IO])
  include(dummy[IO])
  include(shared[IO])

  makeConfig[S3BucketCached.CacheFilesTTL]("aws.s3.cache")

  def prod[F[+_, +_]: TagKK](implicit T: TagK[F[Throwable, ?]]): ModuleDef = new ModuleDef {
    tag(Repo.Prod)

    make[S3BucketFactory[F]].from[S3BucketFactory.Impl[F]]
    make[S3ComponentFactory[F]].from[S3ComponentFactory.Impl[F]]
    make[S3HealthChecker[F]]
    many[HealthChecker[F]].weak[S3HealthChecker[F]]

    include(S3Docker.module[F[Throwable, ?]]("aws.s3"))
  }
  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Dummy)

    make[S3BucketFactory[F]].from[S3BucketFactory.Dummy[F]]
    make[S3ComponentFactory[F]].from[S3ComponentFactory.Empty[F]]
  }
  def shared[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[S3Component[F]].fromResource((_: S3ComponentFactory[F]).mkClient(None, None))
  }
}
