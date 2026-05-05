package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import izumi.distage.plugins.PluginDef
import izumi.reflect.TagKK
import net.playq.tk.loadtool.LoadTool
import net.playq.tk.loadtool.LoadTool.LoadToolConfig
import net.playq.tk.loadtool.scenario.ScenarioGen.*
import net.playq.tk.loadtool.scenario.{ScenarioAny, ScenarioGen, ScenarioRunner}
import zio.IO

object LoadToolPlugin extends PluginDef {
  include(LoadToolPlugin.module[IO])

  def module[F[+_, +_]: TagKK]: ConfigModuleDef = new ConfigModuleDef {
    // could be needed for scenario gen bindings
    addImplicit[TagKK[F]]

    make[ScenarioRunner[F]]
    many[ScenarioGen.Gen[?]]
      .add[IntGen]
      .add[LongGen]
      .add[UUIDGen]
      .add[BooleanGen]
      .add[StringGen]
      .add[DoubleGen]
      .add[ShortGen]
      .add[ByteGen]

    make[LoadTool[F]].from[LoadTool.Impl[F]]
    many[ScenarioAny[F]]

    makeConfig[LoadToolConfig]("loadtool")
  }
}
