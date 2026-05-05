package net.playq.tk.synchro

import izumi.functional.bio.{BlockingIO2, Clock2, Entropy2, F, Fork2, IO2, Primitives2, Temporal2}
import logstage.LogIO2
import net.playq.tk.control.{Forever, InterruptSignal}
import net.playq.tk.postgres.config.PostgresNamespaceConfig
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.postgres.exceptions.SQLConnectorException
import net.playq.tk.postgres.implicits.*
import net.playq.tk.postgres.partitioning.model.{ColumnName, TableName}
import net.playq.tk.postgres.{PostgresConnector, RawSQL}
import net.playq.tk.synchro.TkSynchronizer.SynchronizerLockReleaser
import net.playq.tk.synchro.TkSynchronizerPostgres.{MutexDDL, PostgresMutex}
import net.playq.tk.synchro.config.PostgresSynchronizerConfig

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.duration.FiniteDuration

final class TkSynchronizerPostgres[F[+_, +_]: IO2: Temporal2: Fork2: BlockingIO2: Primitives2](
  mutexTable: MutexDDL,
  config: PostgresSynchronizerConfig,
  clock: Clock2[F],
  entropy: Entropy2[F],
)(implicit
  log: LogIO2[F],
  connector: PostgresConnector[F],
) extends TkSynchronizer[F] {
  import mutexTable.*

  override def tryAcquire(lockPath: String, await: FiniteDuration): F[Nothing, Option[SynchronizerLockReleaser[F]]] = F.shiftBlocking {
    for {
      _   <- log.debug(s"locking on $lockPath for $await with ${config.ttl}")
      res <- tryToAcquireMutexUntilSuccess(lockPath, config.ttl, config.acquireRetrySleep).timeout(await).flatMap {
        case Some(mutex) =>
          for {
            _      <- log.debug(s"Acquired ${mutex.mutexName}.")
            signal <- InterruptSignal.make[F]
            fiber  <- F.fork(F.shiftBlocking {
              Forever.loopForeverWithSignal(s"SynchronizerLockReleaser $lockPath", signal)(
                F.sleep(config.acquireRetrySleep) *>
                heartbeat(mutex, signal)
              )
            })
            lock = new SynchronizerLockReleaser[F] {
              override def release: F[Nothing, Unit] = {
                for {
                  _ <- fiber.interrupt
                  _ <- releaseMutex(mutex)
                } yield ()
              }
            }
          } yield Some(lock)
        case None =>
          log.warn(s"Failed to acquire ${lockPath -> "mutexName"} with ${await -> "timeout"}.").as(None)
      }
    } yield res
  }

  private[synchro] def tryToAcquireMutexUntilSuccess(lockPath: String, ttl: FiniteDuration, acquireRetrySleep: FiniteDuration): F[Nothing, PostgresMutex] = {
    (for {
      mbMutex <- tryToAcquireMutex(lockPath, ttl)
      res      = mbMutex match {
        case Some(value) => F.pure(value)
        case None        => F.sleep(acquireRetrySleep) *> tryToAcquireMutexUntilSuccess(lockPath, ttl, acquireRetrySleep)
      }
    } yield res).flatMap(identity)
  }

  private[synchro] def tryToAcquireMutex(mutexName: String, ttl: FiniteDuration): F[Nothing, Option[PostgresMutex]] = {
    (for {
      _         <- log.debug(s"Try to acquire $mutexName.")
      now       <- clock.nowZoned()
      lockId    <- entropy.nextUUID()
      expiresAt  = now.minusSeconds(ttl.toSeconds)
      mutexName <- connector.query("mutex-acquire") {
        sql"""insert into $table ($lockPathColumn, $updatedAtColumn, $lockIdColumn)
             |values ($mutexName, $now, $lockId)
             |on conflict ($lockPathColumn)
             |do update set
             |  $updatedAtColumn = $now,
             |  $lockIdColumn = excluded.$lockIdColumn
             |where ${table.sqlTable}.$updatedAtColumn < $expiresAt
             |returning $lockPathColumn""".stripMargin.query[String].option
      }
      _ <- F.traverse_(mutexName) {
        mutexName => log.debug(s"Acquired mutex $mutexName $ttl $now")
      }
    } yield {
      mutexName.map(PostgresMutex(now, _, lockId))
    }).catchAll(err => log.error(s"Failed to acquire mutex on $mutexName. $err").as(None))
  }

  private[synchro] def heartbeat(mutex: PostgresMutex, selfInterrupt: InterruptSignal[F]): F[SQLConnectorException, Unit] = {
    for {
      now     <- clock.nowZoned()
      _       <- log.debug(s"Heartbeat ${mutex.mutexName}.")
      updates <- connector.query("mutex-heartbeat") {
        sql"""update $table set $updatedAtColumn = $now
             |where $lockPathColumn = ${mutex.mutexName}
             |and   ($lockIdColumn = ${mutex.lockId} or $lockIdColumn = $zeroUUID)""".stripMargin.update.run
      }
      _ <- F.when(updates <= 0)(for {
        _ <- log.warn(s"${mutex.mutexName} was somehow released or taken by someone else without us running the finalizer first")
        _ <- selfInterrupt.interrupt
      } yield ())
    } yield ()
  }

  private[synchro] def releaseMutex(mutex: PostgresMutex): F[Nothing, Unit] = {
    F.uninterruptible {
      log.debug(s"Release ${mutex.mutexName}") *>
      connector
        .query("mutex-release") {
          sql"""delete from ${mutexTable.table}
               |where $lockPathColumn = ${mutex.mutexName}
               |and   ($lockIdColumn = ${mutex.lockId} or $lockIdColumn = $zeroUUID)""".stripMargin.update.run.void
        }
        .guaranteeOnFailure(err => log.error(s"Failed to release mutex on ${mutex.mutexName}. $err").void)
        .catchAll(_ => F.unit) *>
      log.debug(s"Released ${mutex.mutexName}")
    }
  }
}

object TkSynchronizerPostgres {

  private[synchro] final case class PostgresMutex(
    acquiredAt: ZonedDateTime,
    mutexName: String,
    lockId: UUID,
  )

  final class MutexDDL(schema: PostgresNamespaceConfig) extends DDLComponent {
    val table: TableName = schema table "synchronization_locks"

    val lockPathColumn: ColumnName  = ColumnName("lock_path")
    val updatedAtColumn: ColumnName = ColumnName("updated_at")
    /** lockIdColumn prevents a staggered or repeatedly executed `release` from deleting someone else's lock, makes `release` idempotent as in ZK */
    val lockIdColumn: ColumnName = ColumnName("lock_id")

    val zeroUUID = new UUID(0L, 0L)

    override final val ddl = table.table ->
      sql"""create table if not exists $table (
           |    $lockPathColumn text not null,
           |    $updatedAtColumn timestamp with time zone not null,
           |    primary key ($lockPathColumn)
           |);
           |
           |alter table $table add column if not exists $lockIdColumn uuid not null
           |default '${RawSQL(zeroUUID.toString)}';
           |""".stripMargin
  }

}
