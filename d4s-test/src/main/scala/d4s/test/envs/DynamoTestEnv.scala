package d4s.test.envs

import d4s.config.DynamoConfig
import d4s.test.envs.DynamoTestEnv.DDLDown
import d4s.{DynamoDDLService, DynamoTablesService}
import distage.{DIKey, ModuleDef, TagKK}
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.distage.testkit.TestConfig
import izumi.functional.bio.{Applicative2, F}
import logstage.LogIO2
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, ModuleOverrides, WithProduction}

trait DynamoTestEnv[F[+_, +_]] extends MemoizationRoots with ModuleOverrides with ForcedRoots with WithProduction {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: TestConfig.PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> Set(DIKey[DynamoConfig]))) +
    (1 -> DIKey[DDLDown[F]])

  abstract override def moduleOverrides: distage.Module = super.moduleOverrides ++ new ModuleDef {
    make[DDLDown[F]]
    make[DynamoTablesService.Memo[F]]
    make[DynamoTablesService[F]].using[DynamoTablesService.Memo[F]]
  }

  abstract override def forcedRoots: TestConfig.AxisDIKeys = super.forcedRoots ++ Map(
    Set(Scene.Managed) -> Set(DIKey[DDLDown[F]])
  )
}

object DynamoTestEnv {
  final case class DDLDown[F[+_, +_]: Applicative2](
    dynamoDDLService: DynamoDDLService[F],
    logger: LogIO2[F],
  ) extends Lifecycle.Self[F[Throwable, _], DDLDown[F]] {
    override def acquire: F[Throwable, Unit] = F.unit
    override def release: F[Throwable, Unit] = {
      logger.info("Deleting all tables") *>
      dynamoDDLService.down()
    }
  }
}
