package net.playq.tk.aws.ses.test

import distage.DIKey
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.distage.testkit.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.aws.ses.SESClient
import net.playq.tk.aws.ses.config.SESConfig
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, WithProduction}
import zio.IO

trait SESTestEnv extends MemoizationRoots with ForcedRoots with WithProduction {

  override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++ Map(0 -> Map(Scene.Managed -> Set(DIKey[SESConfig]))) + (1 -> DIKey[SESClient[IO]])

  override protected def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Set(DIKey[SESClient[IO]])
}
