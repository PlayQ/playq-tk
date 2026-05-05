package d4s.test.envs

import d4s.config.DynamoConfig
import d4s.services.{DynamoSetupService, DynamoTablesService}
import d4s.table.DynamoTableDef
import d4s.test.envs.DynamoTestEnvBase.DynamoTablesCleaner
import distage.{DIKey, ModuleDef, TagKK}
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.distage.testkit.model.TestConfig
import izumi.functional.bio.{Applicative2, F}
import logstage.LogIO2
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, ModuleOverrides, WithProduction}

import scala.annotation.unused

trait DynamoTestEnv[F[+_, +_]] extends DynamoTestEnvBase[F] with WithProduction

trait DynamoTestEnvBase[F[+_, +_]] extends MemoizationRoots with ModuleOverrides with ForcedRoots {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: TestConfig.PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> Set(DIKey[DynamoConfig]))) +
    (1 -> DIKey[DynamoTablesCleaner[F]])

  abstract override def moduleOverrides: distage.Module = super.moduleOverrides ++ new ModuleDef {
    make[DynamoTablesCleaner[F]]
    make[DynamoTablesService.Memo[F]]
    make[DynamoTablesService[F]].using[DynamoTablesService.Memo[F]]
  }

  abstract override def forcedRoots: TestConfig.AxisDIKeys = super.forcedRoots ++ Map(
    Set(Scene.Managed) -> Set(DIKey[DynamoTablesCleaner[F]])
  )
}

object DynamoTestEnvBase {
  final case class DynamoTablesCleaner[F[+_, +_]: Applicative2](
    @unused dynamoDDLService: DynamoSetupService[F],
    instances: Set[DynamoTableDef],
    tablesManager: DynamoTablesService[F],
    logger: LogIO2[F],
  ) extends Lifecycle.Self[F[Throwable, _], DynamoTablesCleaner[F]] {
    override def acquire: F[Throwable, Unit] = F.unit
    override def release: F[Throwable, Unit] = {
      logger.info("Deleting all tables") *>
      dropTables()
    }

    def dropTables(): F[Throwable, Unit] = {
      logger.info(s"Dynamo: performing down scripts...") *>
      tablesManager.delete(instances) *>
      logger.info("Dynamo: downing finished.")
    }
  }
}
