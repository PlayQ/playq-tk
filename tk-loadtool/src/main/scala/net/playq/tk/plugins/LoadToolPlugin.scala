package net.playq.tk.plugins

import distage.Tag
import distage.config.ConfigModuleDef
import distage.plugins.PluginDef
import izumi.functional.bio.Ref3
import izumi.reflect.{TagK3, TagKK}
import net.playq.tk.loadtool.LoadTool
import net.playq.tk.loadtool.LoadTool.LoadToolConfig
import net.playq.tk.loadtool.scenario.bio.ScenarioIO2
import net.playq.tk.loadtool.scenario.bio.ScenarioIO2.{ScenarioIO2Syntax, ScenarioIO2SyntaxAux}
import net.playq.tk.loadtool.scenario.gen.ScenarioGen.{IntGen, LongGen, UUIDGen}
import net.playq.tk.loadtool.scenario.gen.{ScenarioGen, ScenarioGenProvider}
import net.playq.tk.loadtool.scenario.{Scenario, ScenarioContext, ScenarioRunner, ScenarioScope}
import zio.{Has, ZIO}

import scala.annotation.unchecked.{uncheckedVariance => v}

object LoadToolPlugin extends PluginDef {
  include(LoadToolPlugin.module[ZIO])

  def module[F[-_, +_, +_]: TagK3](implicit tag0: TagKK[F[Any, +?, ?]]): ConfigModuleDef = new ConfigModuleDef {
    addConverted3To2[F[Any, +?, +?]](tag0)

    addImplicit[Tag[Ref3[ZIO, ScenarioScope]]]
    addImplicit[Tag[ScenarioContext[ZIO[Any, ?, ?]]]]
    addImplicit[Tag[Has[Ref3[ZIO, ScenarioScope]]]]

    make[ScenarioRunner[F]]
    make[ScenarioGenProvider]
    many[ScenarioGen[_]]
      .add[IntGen]
      .add[LongGen]
      .add[UUIDGen]

    makeConfig[LoadToolConfig]("loadtool")

    // workaround for https://github.com/zio/izumi-reflect/issues/82
    def addConverted3To2[G[+e, +a] >: F[Any, e @v, a @v] <: F[Any, e @v, a @v]: TagKK]: Unit = {
      make[LoadTool[G]].from[LoadTool.Impl[F]]
      many[Scenario[G, _, _]]
      make[ScenarioIO2SyntaxAux[G, F[Has[Ref3[F, ScenarioScope]] with Has[ScenarioContext[G]], +?, +?]]]
        .from[ScenarioIO2.Impl[F]]
        .aliased[ScenarioIO2Syntax[G]]
      ()
    }
  }
}
