package net.playq.tk.aws.s3.test

import distage.{DIKey, TagKK}
import izumi.distage.model.definition.StandardAxis.{Repo, Scene}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.model.TestConfig.PriorAxisDIKeys
import net.playq.tk.aws.s3.config.S3Config
import net.playq.tk.aws.s3.{S3Bucket, S3BucketId}
import net.playq.tk.test.{ForcedRoots, MemoizationRoots}

trait S3TestEnv[F[+_, +_]] extends MemoizationRoots with ForcedRoots {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: PriorAxisDIKeys = super.memoizationRoots ++ Map(
    0 -> Map(Scene.Managed -> Set(DIKey[S3Config])),
    1 -> Map(Repo.Prod -> Set(DIKey[Set[S3Bucket[F, S3BucketId]]])),
  )

  abstract override def forcedRoots: TestConfig.AxisDIKeys =
    super.forcedRoots ++ Map(
      Scene.Managed -> Set(DIKey[S3Config]),
      Repo.Prod     -> Set(DIKey[Set[S3Bucket[F, S3BucketId]]]),
    )
}
