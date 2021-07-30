package net.playq.tk.launcher

import distage.DefaultModule2
import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.model.meta.LibraryReference
import izumi.functional.bio.Async2
import izumi.fundamentals.platform.resources.IzArtifactMaterializer
import izumi.reflect.TagKK

abstract class TkAppBase[F[+_, +_]: TagKK: Async2: DefaultModule2] extends RoleAppMain.LauncherBIO2[F] {
  override protected def pluginConfig: PluginConfig          = PluginConfig.cached(Seq("net.playq.tk.plugins"))
  override protected def bootstrapPluginConfig: PluginConfig = PluginConfig.cached(Seq("net.playq.tk.bootstrap"))

  override protected def roleAppBootOverrides(args: RoleAppMain.ArgV): distage.Module = new ModuleDef {
    many[LibraryReference].addValue(LibraryReference("playq-tk", Some(IzArtifactMaterializer.currentArtifact)))
    make[Boolean].named("distage.roles.reflection").fromValue(false)
  }
}
