package d4s.services

import d4s.table.DynamoTableDef
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.Monad2
import logstage.LogIO2
import logstage.LogIO2.log

final class DynamoSetupService[F[+_, +_]: Monad2: LogIO2](
  instances: Set[DynamoTableDef],
  tablesManager: DynamoTablesService[F],
) extends Lifecycle.SelfNoClose[F[Throwable, _], DynamoSetupService[F]] {
  override def acquire: F[Throwable, Unit] = setupTables()

  def setupTables(): F[Throwable, Unit] = {
    for {
      _ <- log.info(s"Dynamo: performing tables setup...")
      _ <- tablesManager.setup(instances)
      _ <- log.info("Dynamo: upping finished.")
    } yield ()
  }
}
