package net.playq.tk.postgres.test

import distage.{DIKey, TagKK}
import izumi.distage.model.definition.StandardAxis.{Repo, Scene}
import izumi.distage.testkit.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.postgres.config.PostgresConfig
import net.playq.tk.postgres.ddl.DDLComponent.DDLUpComponent
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, WithProduction}

trait PostgresTestEnv[F[+_, +_]] extends MemoizationRoots with ForcedRoots with WithProduction {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> DIKey[PostgresConfig])) +
    (1 -> DIKey[DDLUpComponent[F]])

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Map(Repo.Prod -> DIKey[DDLUpComponent[F]])
}
