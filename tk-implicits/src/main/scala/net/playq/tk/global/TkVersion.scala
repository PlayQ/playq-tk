package net.playq.tk.global

import izumi.distage.roles.model.meta.LibraryReference
import izumi.fundamentals.platform.resources.IzArtifactMaterializer

object TkVersion {
  private[tk] val tkLibraryReference: LibraryReference = LibraryReference("playq-tk", Some(IzArtifactMaterializer.currentArtifact))
}
