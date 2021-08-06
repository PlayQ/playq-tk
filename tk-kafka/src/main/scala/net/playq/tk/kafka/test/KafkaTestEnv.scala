package net.playq.tk.kafka.test

import distage.{DIKey, TagKK}
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.testkit.TestConfig.{AxisDIKeys, PriorAxisDIKeys}
import net.playq.tk.kafka.KafkaChecker
import net.playq.tk.kafka.config.KafkaPropsConfig
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, ModuleOverrides, WithProduction}
import net.playq.tk.util.await.TkWaiterFactory

trait KafkaTestEnv[F[+_, +_]] extends ModuleOverrides with WithProduction with ForcedRoots with MemoizationRoots {
  implicit val tagBIO: TagKK[F]

  abstract override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    make[TestDataSubmitter[F]].fromResource[TestDataSubmitter.Impl[F]]
    make[TestStreams[F]].from[TestStreams.Impl[F]]
    make[TkWaiterFactory[F]]
    make[KafkaChecker[F]]
  }

  abstract override def forcedRoots: AxisDIKeys =
    super.forcedRoots ++ Set(DIKey[KafkaChecker[F]])

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++ Map(0 -> Map(Scene.Managed -> Set(DIKey[KafkaPropsConfig]))) + (1 -> DIKey[KafkaChecker[F]])
}
