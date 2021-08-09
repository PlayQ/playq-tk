package net.playq.tk.plugins

import cats.effect.{Blocker, ContextShift}
import com.zaxxer.hikari.HikariDataSource
import distage.plugins.PluginDef
import doobie.hikari.HikariTransactor
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import net.playq.tk.postgres.config.PostgresNamespaceConfig
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.postgres.ddl.DDLComponent.DDLUpComponent
import net.playq.tk.postgres.healthcheck.{PostgresCheckTable, PostgresHealthChecker}
import net.playq.tk.postgres.partitioning.Partitioning
import net.playq.tk.postgres.syntax.TkDoobieLogHandler
import net.playq.tk.postgres.{PostgresConnector, PostgresDataSource}
import net.playq.tk.quantified.AsyncThrowable
import zio.IO

object PostgresPlugin extends PluginDef {
  include(module)
  include(config)

  def module: ModuleDef = new ConfigModuleDef {
    tag(Repo.Prod)

    make[PostgresConnector[IO]].from[PostgresConnector.Impl[IO]]
    make[TkDoobieLogHandler]
    make[PostgresHealthChecker[IO]]

    make[HikariDataSource].fromResource[PostgresDataSource[IO]]
    make[HikariTransactor[IO[Throwable, ?]]].from {
      (hikariDataSource: HikariDataSource, blockingIOExecutionContext: Blocker, F: AsyncThrowable[IO], shift: ContextShift[IO[Throwable, ?]]) =>
        HikariTransactor(hikariDataSource, blockingIOExecutionContext.blockingContext, blockingIOExecutionContext)(F, shift)
    }

    many[PostgresCheckTable]

    many[DDLComponent]
    make[DDLUpComponent[IO]].fromResource[DDLUpComponent[IO]]

    make[Partitioning]
    many[DDLComponent]
      .weak[Partitioning]
  }

  def config: ConfigModuleDef = new ConfigModuleDef {
    makeConfig[PostgresNamespaceConfig]("postgres")
  }
}
