package net.playq.tk.launcher

import distage.{Activation, DefaultModule2, Module}
import izumi.distage.bootstrap.Cycles
import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.LibraryReference
import izumi.functional.bio.Async2
import izumi.fundamentals.platform.resources.{IzArtifact, IzArtifactMaterializer}
import izumi.reflect.TagKK
import net.playq.tk.global.TkVersion

abstract class TkAppBase[F[+_, +_]](
  appArtifact: IzArtifact
)(implicit
  tagK: TagKK[F],
  async: Async2[F],
  defaultModule: DefaultModule2[F],
) extends RoleAppMain.LauncherBIO[F]()(using tagK, async, defaultModule, IzArtifactMaterializer(appArtifact)) {

  override protected def pluginConfig: PluginConfig          = PluginConfig.cached(Seq("net.playq.tk.plugins"))
  override protected def bootstrapPluginConfig: PluginConfig = PluginConfig.cached(Seq("net.playq.tk.bootstrap"))

  /**
    * - add library version to display during startup in [[izumi.distage.roles.launcher.StartupBanner.Impl]]
    * - disable circular dependencies
    * - disable reflective role discovery in favor of `makeRole`
    * - disable activations warnings
    */
  override protected def roleAppBootOverrides(args: RoleAppMain.ArgV): Module = super.roleAppBootOverrides(args) ++ new ModuleDef {
    many[LibraryReference]
      .add(TkVersion.tkLibraryReference)

    make[Activation].named("bootstrap").fromValue(Activation(Cycles -> Cycles.Disable))
    make[Boolean].named("distage.roles.reflection").fromValue(false)
    make[Boolean].named("distage.roles.activation.warn-unset").fromValue(false)
  }
}
