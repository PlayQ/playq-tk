package net.playq.tk.plugins

import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.PluginDef
import izumi.reflect.TagK
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import zio.Task

object Http4sDslPlugin extends PluginDef {
  include(Http4sDslPlugin.module[Task])

  def module[F[_]: TagK]: ModuleDef = new ModuleDef {
    makeTrait[Http4sDsl[F]]
    makeTrait[Http4sClientDsl[F]]
  }
}
