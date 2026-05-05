package net.playq.tk.aws.sns.test
import distage.{DIKey, Repo, Scene, TagKK}
import izumi.distage.testkit.model.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.aws.sns.SNSComponent
import net.playq.tk.aws.sns.config.SNSConfig
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, WithProduction}

trait SNSTestEnv[F[+_, +_]] extends MemoizationRoots with ForcedRoots with WithProduction {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> Set(DIKey[SNSConfig]))) +
    (1 -> DIKey[SNSComponent[F]])

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Map(Repo.Prod -> Set(DIKey[SNSComponent[F]]))
}
