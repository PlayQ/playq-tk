package net.playq.tk.kafka.test

import izumi.distage.model.definition.{Module, ModuleDef}
import net.playq.tk.test.ModuleOverrides
import zio.IO

trait TestStreamsEnv extends ModuleOverrides {
  abstract override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    make[TestStreams[IO]].from[TestStreams.Impl[IO]]
  }
}
