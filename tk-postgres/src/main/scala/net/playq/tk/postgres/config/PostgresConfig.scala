package net.playq.tk.postgres.config

import java.util.Properties

import com.zaxxer.hikari.HikariConfig

import scala.concurrent.duration.FiniteDuration

final case class PostgresConfig(
  jdbcDriver: String,
  url: String,
  user: String,
  password: String,
  defTimeout: FiniteDuration,
  parameters: Map[String, String],
) {
  lazy val hikariConfig: HikariConfig = {
    val properties = new Properties()
    parameters.map {
      case (k, v) =>
        properties.put(k.replace('_', '.'), v)
    }

    val config = new HikariConfig(properties)
    config.setJdbcUrl(url)
    config.setUsername(user)
    config.setPassword(password)
    config.setDriverClassName(jdbcDriver)
    config
  }
}
