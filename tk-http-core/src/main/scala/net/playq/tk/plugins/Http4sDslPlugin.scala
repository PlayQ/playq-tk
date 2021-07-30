package net.playq.tk.plugins

import distage._
import distage.plugins._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import zio.Task

object Http4sDslPlugin extends PluginDef {
  include(Http4sDslPlugin.module[Task])

  def module[F[_]: TagK]: ModuleDef = new ModuleDef {
    make[Http4sDsl[F]]
    make[Http4sClientDsl[F]]
  }
}
