package net.playq.tk.postgres.partitioning

import cats.syntax.applicativeError.*
import cats.syntax.apply.*
import cats.syntax.foldable.*
import cats.syntax.functor.*
import com.zaxxer.hikari.pool.HikariProxyConnection
import doobie.SqlState
import doobie.free.connection.ConnectionIO
import doobie.free.implicits.{AsyncConnectionIO => F}
import doobie.free.{connection, preparedstatement}
import doobie.postgres.sqlstate.class42.UNDEFINED_TABLE
import doobie.syntax.string.*
import doobie.util.fragment.Fragment
import doobie.util.update.Update
import logstage.{IzLogger, LogIO}
import net.playq.tk.postgres.RawSQL
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.postgres.partitioning.Partitioning.PartitioningOnActiveTransactionException
import net.playq.tk.postgres.partitioning.model.{ColumnName, PartitionName, TableName}
import org.postgresql.core.{BaseConnection, TransactionState}

import java.sql.SQLException

final class Partitioning(
  logger: IzLogger
) extends DDLComponent {
  private[this] val log: LogIO[ConnectionIO] = LogIO.fromLogger(logger)

  override final val ddl = "partition-management-configuration" -> sql"""
    set constraint_exclusion=partition;
    -- FIXME: pg 11 features
    -- set enable_parallel_append=on;
    -- set enable_partition_pruning=on;
    -- set enable_partitionwise_aggregate=on;
  """

  def partitionedQuery[K: PartitionKey, A](
    table: TableName,
    partitioner: CreatePartition[K],
    partitionPrimaryKeys: Set[ColumnName],
  )(partitionKey: K
  )(partitionedQuery: PartitionName => ConnectionIO[A]
  ): ConnectionIO[A] = {
    partitionedTransactionWithPrimaryKey(table, partitionKey, partitioner, partitionPrimaryKeys) {
      partitionedQuery(table.partitionOf(partitionKey))
    }
  }

  def partitionQuery[K: PartitionKey, A](
    table: TableName
  )(partitionKey: K
  )(partitionedQuery: PartitionName => ConnectionIO[A]
  ): ConnectionIO[A] = {
    newTransaction(table, partitionKey) {
      partitionedQuery(table.partitionOf(partitionKey))
    }
  }

  def partitionedQueryWithKey[K: PartitionKey, A](
    table: TableName,
    partitioner: CreatePartition[K],
    partitionPrimaryKeys: Set[ColumnName],
  )(partitionKey: K
  )(partitionedQuery: K => ConnectionIO[A]
  ): ConnectionIO[A] = {
    partitionedTransactionWithPrimaryKey(table, partitionKey, partitioner, partitionPrimaryKeys) {
      partitionedQuery(partitionKey)
    }
  }

  def partitionBatchWrite[T: -_ <:< PartitionedWrite[K], K: PartitionKey](
    table: TableName,
    partitioner: CreatePartition[K],
    partitionPrimaryKeys: Set[ColumnName],
  )(writes: Iterable[T]
  )(partitionedQuery: PartitionName => Update[T]
  ): ConnectionIO[Unit] = {
    writes.groupBy(_.partitionKey).to(LazyList).traverse_ {
      case (partitionKey, inserts) =>
        partitionedTransactionWithPrimaryKey(table, partitionKey, partitioner, partitionPrimaryKeys) {
          partitionedQuery(table.partitionOf(partitionKey))
            .updateMany(inserts.to(LazyList))
        }
    }
  }

  /** Dynamically create missing partition if insert failed due to a missing partition.
    * Add a partition specific primary key with the following columns
    */
  private[this] def partitionedTransactionWithPrimaryKey[K: PartitionKey, A](
    table: TableName,
    partitionKey: K,
    partitioner: CreatePartition[K],
    primaryKeys: Set[ColumnName],
  )(query: ConnectionIO[A]
  ): ConnectionIO[A] = {
    val transaction             = newTransaction(table, partitionKey)(query)
    val createPartitionAndRetry = createPartition(table, partitionKey, primaryKeys)(partitioner.apply)

    partitionedTransactionWith(transaction)(createPartitionAndRetry)
  }

  private[this] def partitionedTransactionWith[A](query: ConnectionIO[A])(createPartition: ConnectionIO[Unit]): ConnectionIO[A] = {
    query.recoverWith {
      case sqlException: SQLException =>
        // if the query failed because of undefined table error, create a new partition
        // and retry ONCE
        whenUndefinedTable(sqlException)(createPartition *> query)
    }
  }

  private[this] def whenUndefinedTable[A](sqlError: SQLException)(handler: => ConnectionIO[A]): ConnectionIO[A] = {
    val error    = sqlError.getMessage
    val sqlState = SqlState(sqlError.getSQLState)

    if (sqlState == UNDEFINED_TABLE)
      log.debug(s"Got no table error from postgres $error") *>
      handler
    else
      F.raiseError(sqlError)
  }

  private[this] def createPartition[K: PartitionKey](
    table: TableName,
    partitionKey: K,
    primaryKeys: Set[ColumnName],
  )(createPartition: (TableName, K) => ConnectionIO[Unit]
  ): ConnectionIO[Unit] = {
    val partition = table.partitionOf(partitionKey)
    F.onError(for {
      _ <- log.debug(s"Creating $partition for $table with $partitionKey and retrying")

      // persist DDL change in a new transaction to avoid recreating the partition if a subsequent query fails and to speed up the subsequent query
      _ <- newTransaction(table, partitionKey) {
        createPartition(table, partitionKey) *>
        executeRaw {
          (if (primaryKeys.nonEmpty) {
             val columns = primaryKeys.mkString("(", ", ", ")")
             RawSQL(s"alter table $partition add primary key $columns;")
           } else {
             RawSQL("")
           }).sqlString
        }
      }

      _ <- log.debug(s"Succeeded in creating $partition for $table with $partitionKey, now retrying")
    } yield ()) {
      case sqlError =>
        log.error(s"Creation of $partition for $table with $partitionKey failed because of $sqlError")
    }
  }

  private[this] def newTransaction[A](table: TableName, partitionKey: Any)(query: ConnectionIO[A]): ConnectionIO[A] = {
    F.onError(for {
      _   <- assertNotInTransaction(table, partitionKey)
      _   <- executeRaw("begin transaction")
      res <- query
      _   <- executeRaw("commit transaction")
    } yield res) {
      case _ =>
        executeRaw("rollback transaction")
    }
  }

  private[this] def executeRaw(sql: String): ConnectionIO[Unit] = {
    Fragment.const(sql).execWith(preparedstatement.execute).void
  }

  private[this] def assertNotInTransaction(table: TableName, partitionKey: Any): ConnectionIO[Unit] = {
    assertNotInTransaction(activeTransactionException(table, partitionKey, _))
  }

  private[this] def assertNotInTransaction(err: TransactionState => Throwable): ConnectionIO[Unit] = {
    for {
      tx <- connection.raw {
        case h: HikariProxyConnection => h.unwrap(classOf[BaseConnection]).getTransactionState
        case p                        => p.asInstanceOf[BaseConnection].getTransactionState
      }
      _ <- F.whenA(tx != TransactionState.IDLE) {
        F.raiseError(err(tx))
      }
    } yield ()
  }

  private[this] def activeTransactionException(table: TableName, partitionKey: Any, tx: TransactionState): PartitioningOnActiveTransactionException = {
    new PartitioningOnActiveTransactionException(
      s"""Transaction state $tx detected when initiating a partitioned transaction on table=$table key=$partitionKey
         |You should close previously opened transaction before initiating a partitioned transaction.""".stripMargin
    )
  }
}

object Partitioning {
  final class PartitioningOnActiveTransactionException(message: String) extends RuntimeException(message)
}
