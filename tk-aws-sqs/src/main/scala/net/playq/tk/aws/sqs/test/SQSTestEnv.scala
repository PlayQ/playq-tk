package net.playq.tk.aws.sqs.test

import distage.DIKey
import izumi.distage.model.definition.StandardAxis.{Repo, Scene}
import izumi.distage.testkit.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.aws.sqs.SQSComponent
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, WithProduction}
import zio.IO

trait SQSTestEnv extends MemoizationRoots with ForcedRoots with WithProduction {
  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> Set(DIKey[SQSConfig]))) +
    (1 -> DIKey[SQSComponent[IO]])

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Map(Repo.Prod -> Set(DIKey[SQSComponent[IO]]))
}
