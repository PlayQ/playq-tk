package net.playq.tk.kafka.test

import distage.TagKK
import izumi.distage.model.definition.{Module, ModuleDef}
import net.playq.tk.test.ModuleOverrides

trait TestStreamsEnv[F[+_, +_]] extends ModuleOverrides {
  implicit val tagBIO: TagKK[F]

  abstract override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    make[TestStreams[F]].from[TestStreams.Impl[F]]
  }
}
