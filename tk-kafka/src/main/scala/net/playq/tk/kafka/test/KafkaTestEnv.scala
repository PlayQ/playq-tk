package net.playq.tk.kafka.test

import distage.DIKey
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.testkit.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.kafka.KafkaChecker
import net.playq.tk.kafka.config.KafkaPropsConfig
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, ModuleOverrides, WithProduction}
import net.playq.tk.util.await.TkWaiterFactory
import zio.IO

trait KafkaTestEnv extends ModuleOverrides with WithProduction with ForcedRoots with MemoizationRoots {

  abstract override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    make[TestDataSubmitter[IO]].fromResource[TestDataSubmitter.Impl[IO]]
    make[TestStreams[IO]].from[TestStreams.Impl[IO]]
    make[TkWaiterFactory[IO]]
    make[KafkaChecker[IO]]
  }

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Set(DIKey[KafkaChecker[IO]])

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++ Map(0 -> Map(Scene.Managed -> Set(DIKey[KafkaPropsConfig]))) + (1 -> DIKey[KafkaChecker[IO]])
}
