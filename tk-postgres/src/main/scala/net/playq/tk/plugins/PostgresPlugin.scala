package net.playq.tk.plugins

import com.zaxxer.hikari.HikariDataSource
import distage.Id
import doobie.hikari.HikariTransactor
import izumi.distage.config.ConfigModuleDef
import izumi.distage.model.definition.ModuleDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginDef
import net.playq.tk.health.HealthChecker
import net.playq.tk.postgres.config.{PostgresConfig, PostgresNamespaceConfig}
import net.playq.tk.postgres.ddl.DDLComponent
import net.playq.tk.postgres.ddl.DDLComponent.DDLUpComponent
import net.playq.tk.postgres.docker.PostgresDockerDefault
import net.playq.tk.postgres.healthcheck.{PostgresCheckTable, PostgresHealthChecker}
import net.playq.tk.postgres.partitioning.{Partitioning, Transacting}
import net.playq.tk.postgres.syntax.TkDoobieLogHandler
import net.playq.tk.postgres.{PostgresConnector, PostgresDataSource, SeparatePostgresConnections}
import net.playq.tk.quantified.AsyncThrowable
import zio.{IO, Task}

import scala.concurrent.ExecutionContext

object PostgresPlugin extends PluginDef {
  include(module)
  include(config)

  def module: ModuleDef = new ConfigModuleDef {
    tag(Repo.Prod)

    make[PostgresConnector[IO]].from[PostgresConnector.Impl[IO]]
    make[TkDoobieLogHandler[IO]].from[TkDoobieLogHandler.Impl[IO]]
    make[PostgresHealthChecker[IO]]
    many[HealthChecker[IO]].weak[PostgresHealthChecker[IO]]

    make[HikariDataSource].fromResource[PostgresDataSource[IO]]

    make[HikariTransactor[IO[Throwable, _]]].from {
      (hikariDataSource: HikariDataSource, blockingIOExecutionContext: ExecutionContext @Id("blockingIO"), logHandler: TkDoobieLogHandler[IO], F: AsyncThrowable[IO]) =>
        HikariTransactor.apply[IO[Throwable, _]](hikariDataSource, blockingIOExecutionContext, Some(logHandler))(using F)
    }

    many[PostgresCheckTable]

    DDLComponent.make[IO, Partitioning]
    make[Transacting]

    make[DDLUpComponent[IO]]

    include(rwOnlyConnectionModule)
    include(roConnectionModule)

    // config definition comes through this line from TkContainerDef[PostgresConfig]
    include(PostgresDockerDefault.module[Task]("postgres"))

  }

  def rwOnlyConnectionModule: ModuleDef = new ModuleDef {
    tag(SeparatePostgresConnections.Off)

    make[HikariTransactor[IO[Throwable, _]]].named("pg-readonly").using[HikariTransactor[IO[Throwable, _]]]

    make[PostgresConfig].named("pg-readonly").using[PostgresConfig]
  }

  def roConnectionModule: ModuleDef = new ConfigModuleDef {
    tag(SeparatePostgresConnections.On)

    make[HikariTransactor[IO[Throwable, _]]]
      .named("pg-readonly")
      .from {
        (hikariDataSource: HikariDataSource @Id("pg-readonly"),
          blockingIOExecutionContext: ExecutionContext @Id("blockingIO"),
          logHandler: TkDoobieLogHandler[IO],
          F: AsyncThrowable[IO],
        ) =>
          HikariTransactor.apply[IO[Throwable, _]](hikariDataSource, blockingIOExecutionContext, Some(logHandler))(using F)
      }

    make[HikariDataSource]
      .named("pg-readonly")
      .fromResource[PostgresDataSource[IO]]
      .annotateParameter[PostgresConfig]("pg-readonly")

    makeConfig[PostgresConfig]("postgres-readonly").named("pg-readonly")
  }

  def config: ConfigModuleDef = new ConfigModuleDef {
    makeConfig[PostgresNamespaceConfig]("postgres")
  }
}
