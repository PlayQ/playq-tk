package net.playq.tk.postgres.ddl

import doobie.hikari.HikariTransactor
import doobie.syntax.connectionio.*
import doobie.util.fragment.Fragment
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Applicative2, F}
import logstage.LogIO2
import net.playq.tk.quantified.BracketThrowable

trait DDLComponent {
  def ddl: (String, Fragment)
}

object DDLComponent {

  final class DDLUpComponent[F[+_, +_]: Applicative2: BracketThrowable](
    transactor: HikariTransactor[F[Throwable, _]],
    ddls: Set[DDLComponent],
    log: LogIO2[F],
  ) extends Lifecycle.Self[F[Throwable, _], DDLUpComponent[F]] {

    override def acquire: F[Throwable, Unit] = {
      ddlUp *> log.info(s"DDL Up successful!")
    }

    override def release: F[Throwable, Unit] = F.unit

    private[this] def ddlUp: F[Throwable, Unit] = {
      val numDDLs = ddls.size
      log.info(s"Executing Postgres DDL Up! $numDDLs") *>
      F.traverse_(ddls.toList)(_.ddl match {
        case (ddlName, ddlSql) =>
          log.info(s"Executing DDL for ${ddlName -> "ddl"}") *>
          ddlSql.update.run.transact(transactor).void
      })
    }
  }

}
