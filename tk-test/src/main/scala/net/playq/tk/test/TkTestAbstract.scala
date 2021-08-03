package net.playq.tk.test

import distage.{Activation, DefaultModule2, Module, TagKK}
import io.github.classgraph.ClassGraph
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.definition.StandardAxis.{Mode, Repo, Scene, World}
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.TestConfig.{AxisDIKeys, ParallelLevel, PriorAxisDIKeys}
import izumi.distage.testkit.scalatest.{AssertIO2, Spec2}
import izumi.logstage.api.Log
import net.playq.tk.test.utils.F2ExtractingOps

abstract class TkTestAbstract[F[+_, +_]: TagKK: DefaultModule2]
  extends Spec2[F]
  with AssertIO2[F]
  with F2ExtractingOps
  with WithActivation
  with ModuleOverrides
  with ForcedRoots
  with MemoizationRoots
  with ReferenceConfigOverrides
  with TestConfigOptions {

  override protected def config: TestConfig = {
    val activation = super.config.activation ++ Activation(Mode -> Mode.Test) ++
      (if (dummyStorage) {
         Activation(Repo -> Repo.Dummy, World -> World.Mock, Scene -> Scene.Provided)
       } else {
         Activation(Repo -> Repo.Prod, World -> World.Real, Scene -> Scene.Managed)
       })

    TestConfig(
      pluginConfig          = PluginConfig.cached(Seq("net.playq.tk.plugins")),
      bootstrapPluginConfig = PluginConfig.cached(Seq("net.playq.tk.bootstrap")),
      activation            = activation,
      memoizationRoots      = memoizationRoots,
      forcedRoots           = forcedRoots,
      moduleOverrides       = moduleOverrides,
      parallelEnvs          = parallelEnvExecution,
      parallelSuites        = parallelSuiteExecution,
      parallelTests         = parallelTestExecution,
      configBaseName        = "test",
      configOverrides       = overrideConfig,
      planningOptions       = PlanningOptions(warnOnCircularDeps = warnOnCircularDeps),
      logLevel              = logLevel,
      debugOutput           = debugOutput,
    )
  }

  override final protected def modifyClasspathScan: ClassGraph => ClassGraph = withWhitelistJarsOnly
}

trait TestConfigOptions {
  protected def parallelEnvExecution: ParallelLevel   = ParallelLevel.Unlimited
  protected def parallelSuiteExecution: ParallelLevel = ParallelLevel.Unlimited
  protected def parallelTestExecution: ParallelLevel  = ParallelLevel.Unlimited

  protected def warnOnCircularDeps: Boolean = true
  protected def logLevel: Log.Level         = Log.Level.Info
  protected def debugOutput: Boolean        = false
}

trait ForcedRoots {
  protected def forcedRoots: AxisDIKeys = AxisDIKeys.empty
}

trait ModuleOverrides {
  def moduleOverrides: Module = Module.empty
}

trait ReferenceConfigOverrides {
  protected def overrideConfig: Option[AppConfig] = None
}

trait WithActivation {
  protected def dummyStorage: Boolean
}

trait WithDummy extends WithActivation {
  override protected final def dummyStorage: Boolean = true
}

trait WithProduction extends WithActivation {
  override protected def dummyStorage: Boolean = false
}

trait MemoizationRoots {
  protected def memoizationRoots: PriorAxisDIKeys = PriorAxisDIKeys.empty
}
