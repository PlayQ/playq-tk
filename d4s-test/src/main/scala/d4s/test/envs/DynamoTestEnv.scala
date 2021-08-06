package d4s.test.envs

import d4s.DynamoDDLService
import d4s.test.envs.DynamoTestEnv.DDLDown
import distage.{DIKey, ModuleDef, TagKK}
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.distage.testkit.TestConfig
import izumi.functional.bio.{Applicative2, F}
import logstage.LogIO2
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, ModuleOverrides, WithProduction}

trait DynamoTestEnv[F[+_, +_]] extends MemoizationRoots with ModuleOverrides with ForcedRoots with WithProduction {
  implicit def tagBIO: TagKK[F]

  override protected def memoizationRoots: TestConfig.PriorAxisDIKeys = super.memoizationRoots ++ Map(
    Set(Scene.Managed) -> Set(DIKey[DDLDown[F]])
  )

  override def moduleOverrides: distage.Module = super.moduleOverrides ++ new ModuleDef {
    make[DDLDown[F]]
  }

  override protected def forcedRoots: TestConfig.AxisDIKeys = super.forcedRoots ++ Map(
    Set(Scene.Managed) -> Set(DIKey[DDLDown[F]])
  )
}

object DynamoTestEnv {
  final case class DDLDown[F[+_, +_]: Applicative2](
    dynamoDDLService: DynamoDDLService[F],
    logger: LogIO2[F],
  ) extends Lifecycle.Self[F[Throwable, ?], DDLDown[F]] {
    override def acquire: F[Throwable, Unit] = F.unit
    override def release: F[Throwable, Unit] = {
      logger.info("Deleting all tables") *>
      dynamoDDLService.down()
    }
  }
}
