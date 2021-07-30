package net.playq.tk.postgres.syntax

import doobie.util.Read
import doobie.util.fragment.Fragment
import doobie.util.log.LogHandler
import doobie.{Query0, Update0}
import net.playq.tk.postgres.PostgresConnector
import net.playq.tk.postgres.syntax.LoggedQuerySyntax.LoggedFragmentOps

import scala.language.implicitConversions

// FIXME: inject and configure through DI (e.g. always enable logging in Tests)
trait LoggedQuerySyntax {
  @inline implicit final def toLogHandler[F[_, _]](implicit conn: PostgresConnector[F]): LogHandler = conn.logHandler
  @inline implicit final def ToLoggedFragmentOps(fr: Fragment): LoggedFragmentOps                   = new LoggedFragmentOps(fr)
}

object LoggedQuerySyntax {
  implicit final class LoggedFragmentOps(private val fr: Fragment) extends AnyVal {
    def logQuery[B: Read](implicit h: LogHandler): Query0[B] = fr.queryWithLogHandler(h)
    def logUpdate(implicit h: LogHandler): Update0           = fr.updateWithLogHandler(h)
  }
}
