package net.playq.tk.postgres.ddl

import distage.{ClassConstructor, Functoid, ModuleDef, Tag}
import doobie.hikari.HikariTransactor
import doobie.syntax.connectionio.*
import doobie.util.fragment.Fragment
import izumi.functional.bio.Applicative2
import izumi.reflect.TagKK
import logstage.LogIO2
import net.playq.tk.quantified.MonadCancelThrowable

trait DDLComponent {
  def ddl: (String, Fragment)
}

object DDLComponent {
  def make[F[+_, +_]: TagKK, T <: DDLComponent: Tag: ClassConstructor](implicit mutCtx: ModuleDef#MutationContext): Unit = new mutCtx.dsl {
    this.make[T].fromEffect {
      Functoid.identity[DDLUpComponent[F]].map2(ClassConstructor[T]) {
        (u, c) =>
          u.executeDDL(c)
      }
    }
    many[DDLComponent]
      .weak[T]
  }.discard()

  final class DDLUpComponent[F[+_, +_]: Applicative2: MonadCancelThrowable](
    transactor: HikariTransactor[F[Throwable, _]],
    log: LogIO2[F],
  ) {
    def executeDDL[T <: DDLComponent](ddl: T): F[Throwable, T] = {
      val (ddlName, ddlSql) = ddl.ddl
      log.info(s"Executing DDL for ${ddlName -> "ddl"}") *>
      ddlSql.update.run.transact(transactor).as(ddl)
    }
  }
}
