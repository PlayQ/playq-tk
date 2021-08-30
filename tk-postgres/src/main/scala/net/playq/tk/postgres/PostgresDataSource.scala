package net.playq.tk.postgres

import com.zaxxer.hikari.HikariDataSource
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import izumi.logstage.api.IzLogger
import net.playq.tk.metrics.MetricRegistryWrapper
import net.playq.tk.postgres.config.PostgresConfig

import java.net.URI

final class PostgresDataSource[F[+_, +_]: IO2](
  cfg: PostgresConfig,
  portCheck: PortCheck,
  logger: IzLogger,
  metrics: MetricRegistryWrapper,
) extends Lifecycle.Basic[F[Throwable, _], HikariDataSource]
  with IntegrationCheck[F[Throwable, _]] {

  override def acquire: F[Throwable, HikariDataSource] = IO2 {
    val config = cfg.hikariConfig
    metrics.registry.foreach {
      registry =>
        config.setMetricRegistry(registry)
    }
    new HikariDataSource(config)
  }

  override def release(resource: HikariDataSource): F[Throwable, Unit] = {
    IO2(resource.close())
  }

  override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
    checkConfig() match {
      case ResourceCheck.Success() =>
        val str = cfg.url.stripPrefix("jdbc:")

        val uri                 = URI.create(str)
        val postgresDefaultPort = 5432

        portCheck.checkUri(uri, postgresDefaultPort, s"Couldn't connect to postgres at uri=$uri defaultPort=$postgresDefaultPort")
      case fail =>
        fail
    }
  }

  private[this] def checkConfig(): ResourceCheck = {
    import cfg.*

    val refused = user.isEmpty || url.isEmpty || password.isEmpty || jdbcDriver.isEmpty

    if (refused) {
      logger.error(s"Postgres configs check failed: Missing config values(user or url or password or jdbcDriver) $user $url $password $jdbcDriver")

      ResourceCheck.ResourceUnavailable(s"Postgres configs check failed: Missing config values(user or url or password or jdbcDriver) $cfg", None)
    } else {
      ResourceCheck.Success()
    }
  }

}
