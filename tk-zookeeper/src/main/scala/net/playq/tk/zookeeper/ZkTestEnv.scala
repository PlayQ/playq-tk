package net.playq.tk.zookeeper

import distage.{DIKey, TagKK}
import izumi.distage.model.definition.StandardAxis.{Repo, Scene}
import izumi.distage.testkit.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, WithProduction}
import net.playq.tk.zookeeper.config.ZookeeperConfig

trait ZkTestEnv[F[+_, +_]] extends MemoizationRoots with ForcedRoots with WithProduction {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> Set(DIKey[ZookeeperConfig])))

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Map(Repo.Prod -> Set(DIKey[ZkComponent[F]]))
}
