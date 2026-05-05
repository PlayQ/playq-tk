package net.playq.tk.postgres.test

import distage.{DIKey, Scene, TagKK}
import doobie.hikari.HikariTransactor
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.testkit.model.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, WithProduction}

trait PostgresTestEnv[F[+_, +_]] extends MemoizationRoots with ForcedRoots with WithProduction {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> DIKey[HikariTransactor[F[Throwable, _]]])) +
    (1 -> DIKey[Set[DDLComponent]])

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++
    Map(Repo.Prod -> DIKey[Set[DDLComponent]])
}
