package net.playq.tk.envs

import distage.config.ConfigModuleDef
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.model.reflection.DIKey
import izumi.distage.testkit.model.TestConfig
import izumi.reflect.TagKK
import net.playq.tk.envs.TestDeadlineEnv.TestDeadlineConfigModule
import net.playq.tk.test.config.TestDeadlineConfig
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, ModuleOverrides, TestDeadline}

trait TestDeadlineEnv[F[+_, +_]] extends ModuleOverrides with MemoizationRoots with ForcedRoots {
  implicit val tagBIO: TagKK[F]

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    include(TestDeadlineConfigModule)
    make[TestDeadline[F]].fromResource[TestDeadline.Resource[F]]
  }

  override def memoizationRoots: TestConfig.PriorAxisDIKeys = super.memoizationRoots ++ Map(
    0 -> Set(DIKey[TestDeadline[F]])
  )

  override def forcedRoots: TestConfig.AxisDIKeys = super.forcedRoots ++ Set(
    DIKey[TestDeadline[F]]
  )
}

object TestDeadlineEnv {
  // makeConfig breaks memoization (config decoder lambda is a different instance on every instantiation), so make config module gloal
  object TestDeadlineConfigModule extends ConfigModuleDef {
    makeConfig[TestDeadlineConfig]("testDeadline")
  }
}
