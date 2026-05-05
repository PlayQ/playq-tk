package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import izumi.distage.roles.model.meta.LibraryReference
import net.playq.tk.global.TkVersion

object TkVersionPlugin extends PluginDef {
  many[LibraryReference]
    .ref[LibraryReference]("playq-tk")
  make[LibraryReference].named("playq-tk").fromValue(TkVersion.tkLibraryReference)
}
