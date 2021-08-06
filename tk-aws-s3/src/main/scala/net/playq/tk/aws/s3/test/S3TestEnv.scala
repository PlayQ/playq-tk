package net.playq.tk.aws.s3.test

import distage.{DIKey, TagKK}
import izumi.distage.model.definition.StandardAxis.{Repo, Scene}
import izumi.distage.testkit.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.aws.s3.S3Component
import net.playq.tk.aws.s3.config.S3Config
import net.playq.tk.test.{ForcedRoots, MemoizationRoots}

trait S3TestEnv[F[+_, +_]] extends MemoizationRoots with ForcedRoots {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> Set(DIKey[S3Config]))) +
    (1 -> DIKey[S3Component[F]])

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Map(Repo.Prod -> DIKey[S3Component[F]])
}
