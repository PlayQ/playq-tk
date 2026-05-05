package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import distage.{TagK, TagKK}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.s3.*
import net.playq.tk.aws.s3.config.S3Config.S3BucketsConfig
import net.playq.tk.aws.s3.config.S3RetryConfig
import net.playq.tk.aws.s3.docker.S3Docker
import net.playq.tk.aws.s3.health.S3HealthChecker
import net.playq.tk.health.HealthChecker
import zio.IO

object AwsS3Plugin extends PluginDef {
  include(prod[IO])
  include(dummy[IO])
  include(shared[IO])
  include(config)

  def prod[F[+_, +_]: TagKK](implicit T: TagK[F[Throwable, _]]): ModuleDef = new ModuleDef {
    tag(Repo.Prod)

    make[S3BucketFactory[F]].from[S3BucketFactory.Impl[F]]
    make[S3ComponentFactory[F]].from[S3ComponentFactory.Impl[F]]

    include(S3Docker.module[F[Throwable, _]]("aws.s3"))
  }

  def dummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Dummy)

    make[S3BucketFactory[F]].from[S3BucketFactory.Dummy[F]]
    make[S3ComponentFactory[F]].from[S3ComponentFactory.Empty[F]]
  }

  def shared[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[S3Component[F]].fromResource((_: S3ComponentFactory[F]).mkComponent(None, None, S3RetryConfig.default))
    make[S3Cache[F]].fromResource[S3Cache.Resource[F]]

    many[S3Bucket[F, S3BucketId]]
    make[S3HealthChecker[F]]
    many[HealthChecker[F]].weak[S3HealthChecker[F]]
  }

  object config extends ConfigModuleDef {
    makeConfig[S3BucketsConfig]("aws.s3")
    makeConfig[S3Cache.S3CacheConfig]("aws.s3.cache")
  }
}
