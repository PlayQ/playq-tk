package net.playq.tk.test

import distage.{Activation, DefaultModule2, Module, TagKK}
import io.github.classgraph.ClassGraph
import izumi.distage.config.model.AppConfig
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.definition.StandardAxis.{Mode, Repo, Scene, World}
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.TestConfig.{AxisDIKeys, Parallelism, PriorAxisDIKeys}
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.scalatest.{AssertIO2, Spec2}
import izumi.distage.testkit.spec.TestConfiguration
import izumi.logstage.api.Log
import net.playq.tk.test.utils.F2ExtractingOps

abstract class TkTestBaseNoCtx[F[+_, +_]: TagKK: DefaultModule2](
) extends Spec2[F]
  with AssertIO2[F]
  with F2ExtractingOps
  with TkTestBaseConfig {
  override final def modifyClasspathScan: ClassGraph => ClassGraph = withWhitelistJarsOnly
}

trait TkTestBaseConfig
  extends TestConfiguration
  with WithActivation
  with ModuleOverrides
  with ForcedRoots
  with MemoizationRoots
  with ReferenceConfigOverrides
  with TestConfigOptions {

  abstract override protected def config: TestConfig = {
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
      configOverrides       = configOverrides,
      planningOptions       = PlanningOptions(warnOnCircularDeps = warnOnCircularDeps),
      logLevel              = logLevel,
      debugOutput           = debugOutput,
      activationStrategy    = TestActivationStrategy.LoadConfig(ignoreUnknownActivations, unsetActivationWarns),
    )
  }
}

trait TestConfigOptions {
  protected def ignoreUnknownActivations: Boolean   = false
  protected def unsetActivationWarns: Boolean       = false
  protected def parallelEnvExecution: Parallelism   = Parallelism.Unlimited
  protected def parallelSuiteExecution: Parallelism = Parallelism.Unlimited
  protected def parallelTestExecution: Parallelism  = Parallelism.Unlimited

  protected def warnOnCircularDeps: Boolean = true
  protected def logLevel: Log.Level         = Log.Level.Info
  protected def debugOutput: Boolean        = false
}

trait ForcedRoots {
  def forcedRoots: AxisDIKeys = AxisDIKeys.empty
}

trait ModuleOverrides {
  def moduleOverrides: Module = Module.empty
}

trait ReferenceConfigOverrides {
  protected def configOverrides: Option[AppConfig] = None
}

trait WithActivation {
  protected def dummyStorage: Boolean
}

trait WithDummy extends WithActivation {
  override protected final def dummyStorage: Boolean = true
}

trait WithProduction extends WithActivation {
  override protected final def dummyStorage: Boolean = false
}

trait MemoizationRoots {
  def memoizationRoots: PriorAxisDIKeys = PriorAxisDIKeys.empty
}
