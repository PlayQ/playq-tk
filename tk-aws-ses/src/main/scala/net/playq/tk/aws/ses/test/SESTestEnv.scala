package net.playq.tk.aws.ses.test

import distage.{DIKey, Repo, Scene, TagKK}
import izumi.distage.testkit.model.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.aws.ses.config.SESConfig
import net.playq.tk.aws.ses.{SESClient, SESComponent}
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, WithProduction}

trait SESTestEnv[F[+_, +_]] extends MemoizationRoots with ForcedRoots with WithProduction {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> DIKey[SESConfig])) +
    (1 -> DIKey[SESComponent[F]])

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Map(Repo.Prod -> DIKey[SESClient[F]])
}
