package net.playq.tk

import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.dsl.ModuleDefDSL.{MakeDSLNamedAfterFrom, MakeDSLUnnamedAfterFrom}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.reflect.Tag

import scala.annotation.unused

// FIXME: remove after izumi 1.2.21 (or 1.2.22 ?) when ConfigModuleDef is fixed to work better on Scala.js
trait ScalajsFixedConfigModuleDefImpl extends ModuleDef {
  final def makeConfig[T: Tag](@unused path: String): MakeDSLUnnamedAfterFrom[T] = {
    make[T].from((throw new RuntimeException("config reading is not supported on Scala.js")): T)
  }
  final def makeConfigNamed[T: Tag](path: String): MakeDSLNamedAfterFrom[T] = {
    make[T].named(path).from((throw new RuntimeException("config reading is not supported on Scala.js")): T)
  }
  final def makeConfigWithDefault[T: Tag](
    @unused path: String
  )(default: => T
  )(implicit pos: CodePositionMaterializer
  ): MakeDSLUnnamedAfterFrom[T] = {
    make[T].from(default)
  }
}
