package d4s.services

import d4s.config.{DynamoBatchConfig, DynamoConfig, DynamoTransactionConfig}
import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryLogger}
import d4s.query.predef.{CreateBackup, DeleteTable, ListTables, SetupTable}
import d4s.table.{DynamoTableDef, DynamoTableRef, TablePrefix}
import izumi.functional.bio.{Applicative2, Async2, F, Temporal2}
import logstage.LogIO2
import net.playq.tk.quantified.ConcurrentThrowable

import java.time.ZonedDateTime
import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.matching.Regex

trait DynamoTablesService[F[_, _]] {
  def setup(tables: Set[DynamoTableDef]): F[DynamoException, Unit]
  def setupPrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit]

  def markForDeletion(tables: Set[DynamoTableDef]): F[DynamoException, Unit]
  def markForDeletionPrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit]

  def delete(tables: Set[DynamoTableDef]): F[DynamoException, Unit]
  def deletePrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit]

  def backup(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit]
  def backupPrefixed[P: TablePrefix](prefix: P)(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit]

  def backupAndDelete(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit]
  def backupAndDeletePrefixed[P: TablePrefix](prefix: P)(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit]

  def listTables: F[DynamoException, List[String]]
  def listTablesByRegex(regex: Regex): F[DynamoException, List[String]]
}

object DynamoTablesService {

  final class Empty[F[+_, +_]: Applicative2] extends DynamoTablesService[F] {
    override def setup(tables: Set[DynamoTableDef]): F[DynamoException, Unit]                                                                  = F.unit
    override def setupPrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit]                               = F.unit
    override def markForDeletion(tables: Set[DynamoTableDef]): F[DynamoException, Unit]                                                        = F.unit
    override def markForDeletionPrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit]                     = F.unit
    override def listTables: F[DynamoException, List[String]]                                                                                  = F.pure(List.empty)
    override def listTablesByRegex(regex: Regex): F[DynamoException, List[String]]                                                             = F.pure(List.empty)
    override def delete(tables: Set[DynamoTableDef]): F[DynamoException, Unit]                                                                 = F.unit
    override def deletePrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit]                              = F.unit
    override def backup(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit]                                             = F.unit
    override def backupPrefixed[P: TablePrefix](prefix: P)(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit]          = F.unit
    override def backupAndDelete(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit]                                    = F.unit
    override def backupAndDeletePrefixed[P: TablePrefix](prefix: P)(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit] = F.unit
  }

  // For test purposes
  final class Memo[F[+_, +_]: Async2: Temporal2: ConcurrentThrowable](
    dynamoClient: DynamoClient[F],
    batchConfig: DynamoBatchConfig,
    transactConfig: DynamoTransactionConfig,
    dynamoConfig: DynamoConfig,
    logger: LogIO2[F],
  ) extends DynamoTablesService.Impl[F](dynamoClient, batchConfig, transactConfig, dynamoConfig, logger) {
    val prefixedTables: mutable.Set[String] = mutable.Set.empty[String]

    override def setupPrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit] = {
      prefixedTables ++= tables.map(_.ref.withPrefix(prefix).tableName)
      super.setupPrefixed(prefix)(tables)
    }

    override def markForDeletionPrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit] = {
      prefixedTables --= tables.map(_.ref.withPrefix(prefix).tableName)
      super.markForDeletionPrefixed(prefix)(tables)
    }

    override def deletePrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit] = {
      prefixedTables --= tables.map(_.ref.withPrefix(prefix).tableName)
      super.deletePrefixed(prefix)(tables)
    }

    override def backupAndDeletePrefixed[P: TablePrefix](prefix: P)(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit] = {
      prefixedTables --= tables.map(_.ref.withPrefix(prefix).tableName)
      super.backupAndDeletePrefixed(prefix)(now, tables)
    }
  }

  sealed class Impl[F[+_, +_]: Async2: Temporal2: ConcurrentThrowable](
    dynamoClient: DynamoClient[F],
    batchConfig: DynamoBatchConfig,
    transactConfig: DynamoTransactionConfig,
    dynamoConfig: DynamoConfig,
    log: LogIO2[F],
  ) extends DynamoTablesService[F] {

    override def setup(tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      setup(tables, identity)

    override def setupPrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      setup(tables, _.withPrefix(prefix))

    override def markForDeletion(tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      markForDeletion(tables, identity)

    override def markForDeletionPrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      markForDeletion(tables, _.withPrefix(prefix))

    override def delete(tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      delete(tables, identity)

    override def deletePrefixed[P: TablePrefix](prefix: P)(tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      delete(tables, _.withPrefix(prefix))

    override def backup(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      backup(now, tables, identity)

    override def backupPrefixed[P: TablePrefix](prefix: P)(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      backup(now, tables, _.withPrefix(prefix))

    override def backupAndDelete(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      backupAndDelete(now, tables, identity)

    override def backupAndDeletePrefixed[P: TablePrefix](prefix: P)(now: ZonedDateTime, tables: Set[DynamoTableDef]): F[DynamoException, Unit] =
      backupAndDelete(now, tables, _.withPrefix(prefix))

    override def listTables: F[DynamoException, List[String]] = {
      run(ListTables().decode(_.tableNames().asScala.toList).readAll)
    }

    override def listTablesByRegex(regex: Regex): F[DynamoException, List[String]] = {
      listTables.map(_.flatMap(regex.findFirstIn))
    }

    private[this] def setup(tables: Set[DynamoTableDef], tweak: DynamoTableRef[?] => DynamoTableRef[?]): F[DynamoException, Unit] = {
      F.parTraverseNCore_(tables) {
        table =>
          val tableRef = tweak(table.ref)
          log.info(s"Going to setup ${tableRef.tableName -> "table"}; ${tableRef.provisioning -> "provisioning"}.") *>
          run(SetupTable(tableRef))
      }
    }

    private[this] def markForDeletion(tables: Set[DynamoTableDef], tweak: DynamoTableRef[?] => DynamoTableRef[?]): F[DynamoException, Unit] = {
      F.parTraverseNCore_(tables) {
        table =>
          val tableRef = tweak(table.ref)
          run(tableRef.describe.optTableNotExist).flatMap {
            case Some(desc) =>
              val arn = desc.table().tableArn()
              log.info(s"Going to mark table for deletion ${tableRef.tableName -> "table"}, $arn") *>
              run(tableRef.markForDeletion(arn))
            case None =>
              F.unit
          }.catchAll {
            error =>
              log.error(s"Error when mark ${tableRef.tableName -> "table"} for deletion: $error")
          }
      }
    }

    private[this] def delete(tables: Set[DynamoTableDef], tweak: DynamoTableRef[?] => DynamoTableRef[?]): F[DynamoException, Unit] = {
      F.parTraverseNCore_(tables) {
        table =>
          val tableRef = tweak(table.ref)
          (for {
            _ <- log.info(s"Going to delete ${tableRef.tableName -> "table"}.")
            _ <- run(DeleteTable(tableRef))
          } yield ()).catchAll {
            error =>
              log.error(s"Error when deleting ${tableRef.tableName -> "table"}: $error")
          }
      }
    }

    private[this] def backup(now: ZonedDateTime, tables: Set[DynamoTableDef], tweak: DynamoTableRef[?] => DynamoTableRef[?]): F[DynamoException, Unit] = {
      F.parTraverseNCore_(tables) {
        table =>
          val tableRef = tweak(table.ref)
          for {
            _ <- log.info(s"Going to backup ${tableRef.tableName -> "table"}.")
            _ <- run(CreateBackup(tableRef, genBackupName(now, tableRef)))
          } yield ()
      }
    }

    private[this] def backupAndDelete(now: ZonedDateTime, tables: Set[DynamoTableDef], tweak: DynamoTableRef[?] => DynamoTableRef[?]): F[DynamoException, Unit] = {
      F.parTraverseNCore_(tables) {
        table =>
          val tableRef = tweak(table.ref)
          (for {
            _           <- log.info(s"Going to backup and delete ${tableRef.tableName -> "table"}.")
            tableExists <- run(tableRef.describe.optTableNotExist.map(_.nonEmpty))
            _           <- {
              if (tableExists) {
                for {
                  _ <- run(CreateBackup(tableRef, genBackupName(now, tableRef)))
                  _ <- run(DeleteTable(tableRef))
                } yield ()
              } else {
                log.info(s"Table do not exists ${tableRef.tableName -> "table"}. Skipping deletion.")
              }
            }
          } yield ()).catchAll {
            error =>
              log.error(s"Error when creating a backup and deleting ${tableRef.tableName -> "table"}: $error")
          }
      }
    }

    private[this] def run[DR[+d] <: DynamoQuery[DR, d], A](q: DR[A]): F[DynamoException, A] = {
      q.execute(new QueryCtx(dynamoClient, log, batchConfig, transactConfig, dynamoConfig), QueryLogger.default)
    }

    private[this] def genBackupName(now: ZonedDateTime, tableRef: DynamoTableRef[?]): String = {
      val nowEpoch = now.toEpochSecond
      s"${tableRef.tableName}_$nowEpoch"
    }
  }
}
