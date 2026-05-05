package net.playq.tk.postgres.partitioning

import cats.effect.Outcome
import cats.syntax.functor.*
import com.zaxxer.hikari.pool.HikariProxyConnection
import doobie.free.connection.ConnectionIO
import doobie.free.implicits.WeakAsyncConnectionIO as F
import doobie.free.{connection, preparedstatement}
import doobie.util.fragment.Fragment
import org.postgresql.core.{BaseConnection, TransactionState}

final class Transacting {

  def withNewTransaction[A](err: TransactionState => Throwable)(query: ConnectionIO[A]): ConnectionIO[A] = {
    F.bracketCase(acquire = {
      for {
        _ <- assertNotInTransaction(err)
        _ <- executeRaw("begin transaction")
      } yield ()
    })(use = _ => query)(release =
      (_, exit) =>
        exit match {
          case Outcome.Succeeded(f) =>
            f.flatMap(_ => executeRaw("commit transaction"))

          case Outcome.Errored(_) | Outcome.Canceled() =>
            executeRaw("rollback transaction")
        }
    )
  }

  def executeRaw(sql: String): ConnectionIO[Unit] = {
    Fragment.const(sql).execWith(preparedstatement.execute).void
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

}
