package net.playq.tk.postgres.partitioning

import doobie.free.implicits.WeakAsyncConnectionIO as F
import cats.syntax.applicativeError.*
import cats.syntax.apply.*
import cats.syntax.foldable.*
import doobie.SqlState
import doobie.free.connection.ConnectionIO
import doobie.postgres.sqlstate.class40.DEADLOCK_DETECTED
import doobie.postgres.sqlstate.class42.{DUPLICATE_TABLE, INVALID_TABLE_DEFINITION, UNDEFINED_TABLE}
import doobie.syntax.string.*
import doobie.util.update.Update
import logstage.{IzLogger, LogIO}
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.postgres.partitioning.Partitioning.PartitioningOnActiveTransactionException
import net.playq.tk.postgres.partitioning.model.{PartitionName, TableName}
import org.postgresql.core.TransactionState

import java.sql.SQLException
import scala.annotation.unused
import scala.collection.Factory

final class Partitioning(
  logger: IzLogger,
  transacting: Transacting,
) extends DDLComponent {
  private val log: LogIO[ConnectionIO] = LogIO.fromLogger(logger)

  override final val ddl = "partition-management-configuration" -> sql"""
    set constraint_exclusion=partition;
    -- pg 11+ features
    set enable_parallel_append=on;
    set enable_partition_pruning=on;
    set enable_partitionwise_aggregate=on;
  """

  /**
    * Perform query in partition, create partition using `partitioner` if it doesn't exist.
    *
    * Passes `table.partitionOf(partitionKey)` to `partitionedQuery`
    */
  def partitionedQuery[K: PartitionKey, A](
    table: TableName,
    partitioner: CreatePartition[K],
  )(partitionKey: K
  )(query: PartitionName => ConnectionIO[A]
  ): ConnectionIO[A] = {
    partitionedTransaction(table, partitionKey, partitioner) {
      query(table.partitionOf(partitionKey))
    }
  }

  /**
    * Perform query in partition, return `None` if it doesn't exist.
    *
    * Passes `table.partitionOf(partitionKey)` to `partitionedQuery`.
    *
    * Does NOT open a new transaction.
    */
  def partitionedRoOptionQuery[K: PartitionKey, A](
    table: TableName
  )(partitionKey: K
  )(query: PartitionName => ConnectionIO[Option[A]]
  ): ConnectionIO[Option[A]] = {
    partitionedRoQueryWith(table, _ => F.pure(Option.empty[A]))(partitionKey)(query)
  }

  /**
    * Perform query in partition, return empty collection if it doesn't exist.
    *
    * Passes `table.partitionOf(partitionKey)` to `partitionedQuery`
    *
    * Does NOT open a new transaction.
    */
  def partitionedRoListQuery[K: PartitionKey, A, CC[_]](
    table: TableName
  )(partitionKey: K
  )(query: PartitionName => ConnectionIO[CC[A]]
  )(implicit factory: Factory[A, CC[A]]
  ): ConnectionIO[CC[A]] = {
    partitionedRoQueryWith(table, _ => F.pure(factory.fromSpecific(Nil)))(partitionKey)(query)
  }

  /**
    * Perform query in partition, call `onMissingPartition` if it doesn't exist.
    *
    * Passes `table.partitionOf(partitionKey)` to `partitionedQuery`
    *
    * Does NOT open a new transaction.
    */
  def partitionedRoQueryWith[K: PartitionKey, A](
    table: TableName,
    onMissingPartition: SQLException => ConnectionIO[A],
  )(partitionKey: K
  )(query: PartitionName => ConnectionIO[A]
  ): ConnectionIO[A] = {
    query(table.partitionOf(partitionKey)).recoverWith {
      case sqlError: SQLException =>
        whenUndefinedTable(sqlError)(onMissingPartition(sqlError))
    }
  }

  /** Wraps `partitionedQuery` in a new transaction */
  def withNewTransaction[K, A](
    table: TableName
  )(partitionKey: K
  )(query: ConnectionIO[A]
  )(implicit @unused k: PartitionKey[K]
  ): ConnectionIO[A] = {
    withNewTransaction(table, partitionKey) {
      query
    }
  }

  /**
    * Perform query in partition, create partition using `partitioner` if it doesn't exist.
    *
    * Passes `partitionKey` to `partitionedQuery`, partition name can be recovered via `table.partitionOf(partitionKey)`
    */
  def partitionedQueryWithKey[K: PartitionKey, A](
    table: TableName,
    partitioner: CreatePartition[K],
  )(partitionKey: K
  )(query: K => ConnectionIO[A]
  ): ConnectionIO[A] = {
    partitionedTransaction(table, partitionKey, partitioner) {
      query(partitionKey)
    }
  }

  /**
    * Perform batch updates, grouped by partitions found in input data. Creates partitions using `partitioner` if they don't exist.
    *
    * Passes `table.partitionOf(partitionKey)` to `partitionedQuery`, where `partitionKey` is extracted from input data.
    */
  def partitionBatchWrite[T, K: PartitionKey](
    table: TableName,
    partitioner: CreatePartition[K],
  )(writes: Iterable[T]
  )(extractor: PartitionedWrite[T, K]
  )(query: PartitionName => Update[T]
  ): ConnectionIO[Unit] = {
    writes.groupBy(extractor.getPartitionKey).to(LazyList).traverse_ {
      case (partitionKey, inserts) =>
        partitionedTransaction(table, partitionKey, partitioner) {
          query(table.partitionOf(partitionKey))
            .updateMany(inserts.to(LazyList))
        }
    }
  }

  /** Create partition in a separate transaction if it doesn't exist yet */
  def _hacky_createPartitionIfNotExists[K: PartitionKey](t: TableName, p: CreatePartition[K])(k: K): ConnectionIO[PartitionName] = {
    partitionedQuery(t, p)(k)(partition => sql"""select 1 from $partition limit 1""".query[Int].option.map(_ => partition))
  }

  /** Dynamically create missing partition if insert failed due to a missing partition. */
  private def partitionedTransaction[K: PartitionKey, A](
    table: TableName,
    partitionKey: K,
    partitioner: CreatePartition[K],
  )(query: ConnectionIO[A]
  ): ConnectionIO[A] = {
    val queryTransaction = withNewTransaction(table, partitionKey)(query)
    val createPartition  = createPartitionWith(table, partitionKey)(partitioner.apply)

    partitionedTransactionWith(createPartition)(queryTransaction)
  }

  private def createPartitionWith[K: PartitionKey](
    table: TableName,
    partitionKey: K,
  )(createPartition: (TableName, K) => ConnectionIO[Unit]
  ): ConnectionIO[Unit] = {
    val partition = table.partitionOf(partitionKey)
    F.onError(for {
      _ <- log.debug(s"Creating $partition for $table with $partitionKey and retrying")

      // persist DDL change using a new transaction to avoid recreating the partition if a subsequent query fails and to speed up the subsequent query
      _ <- retryDeadlocks(20) {
        ignoreDuplicateTableError {
          withNewTransaction(table, partitionKey) {
            createPartition(table, partitionKey)
          }
        }
      }

      _ <- log.debug(s"Succeeded in creating $partition for $table with $partitionKey, now retrying")
    } yield ()) {
      case sqlError =>
        log.error(s"Creation of $partition for $table with $partitionKey failed because of $sqlError")
    }
  }

  private def withNewTransaction[A](table: TableName, partitionKey: Any)(query: ConnectionIO[A]): ConnectionIO[A] = {
    transacting.withNewTransaction(err = activeTransactionException(table, partitionKey, _))(query)
  }

  private def activeTransactionException(table: TableName, partitionKey: Any, tx: TransactionState): PartitioningOnActiveTransactionException = {
    new PartitioningOnActiveTransactionException(
      s"""Transaction state $tx detected when initiating a partitioned transaction on table=$table key=$partitionKey
         |You should close previously opened transactions before initiating a partitioned transaction.""".stripMargin
    )
  }
  private def partitionedTransactionWith[A](createPartition: ConnectionIO[Unit])(transaction: ConnectionIO[A]): ConnectionIO[A] = {
    retryDeadlocks(20) {
      transaction.recoverWith {
        case sqlError: SQLException =>
          // if the query failed because of undefined table error, create a new partition
          // and retry ONCE
          whenUndefinedTable(sqlError)(createPartition *> transaction)
      }
    }
  }

  private def whenUndefinedTable[A](sqlError: SQLException)(handler: => ConnectionIO[A]): ConnectionIO[A] = {
    val error    = sqlError.getMessage
    val sqlState = SqlState(sqlError.getSQLState)

    if (sqlState == UNDEFINED_TABLE) {
      log.debug(s"Got no table error from postgres $error") *>
      handler
    } else if (sqlState == INVALID_TABLE_DEFINITION) {
      log.debug(s"Got invalid table definition error from postgres $error") *>
      handler
    } else {
      F.raiseError(sqlError)
    }
  }

  private def ignoreDuplicateTableError(transaction: ConnectionIO[Unit]): ConnectionIO[Unit] = {
    transaction.recoverWith {
      case sqlError: SQLException =>
        val error    = sqlError.getMessage
        val sqlState = SqlState(sqlError.getSQLState)

        if (sqlState == DUPLICATE_TABLE) {
          log.debug(s"Got duplicate table error from postgres $error")
        } else {
          F.raiseError(sqlError)
        }
    }
  }

  private def retryDeadlocks[A](retries: Int)(transaction: ConnectionIO[A]): ConnectionIO[A] = {
    transaction.recoverWith {
      case sqlError: SQLException =>
        val error    = sqlError.getMessage
        val sqlState = SqlState(sqlError.getSQLState)

        if ((sqlState == DEADLOCK_DETECTED || error.contains("ERROR: multiple primary keys for table")) && retries > 0) {
          log.debug(s"Got deadlock detected from postgres $error") *>
          retryDeadlocks(retries - 1)(transaction)
        } else {
          F.raiseError(sqlError)
        }
    }
  }

}

object Partitioning {
  final class PartitioningOnActiveTransactionException(message: String) extends RuntimeException(message)
}
