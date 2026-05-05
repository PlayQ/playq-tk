package net.playq.tk.postgres.docker

import izumi.distage.docker.Docker.{AvailablePort, DockerPort}
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.docker.TkContainerDef
import net.playq.tk.postgres.config.PostgresConfig

abstract class PostgresDockerBase extends TkContainerDef[PostgresConfig] {
  self: Singleton =>
  def postgresTag: String

  final override def registry: Option[String]      = Some("public.ecr.aws")
  final override def image: String                 = "docker/library/postgres:16.6"
  final override def containerPort: DockerPort.TCP = DockerPort.TCP(5432)

  val user     = "postgres"
  val password = "postgres"
  val db       = "default"

  override def rewriteConfig(conf: PostgresConfig)(ports: NEList[AvailablePort]): PostgresConfig = {
    val port = ports.head
    conf.copy(
      url      = s"jdbc:postgresql://${port.host.host}:${port.port}/$db",
      user     = user,
      password = password,
    )
  }

  override def config: Config = super.config.copy(
    env = Map(
      "POSTGRES_USER"        -> user,
      "POSTGRES_PASSWORD"    -> password,
      "POSTGRES_DB"          -> db,
      "POSTGRES_INITDB_ARGS" -> "--encoding=UTF-8 --lc-collate=C --lc-ctype=C",
    ),
    userTags    = Map("postgres-docker-tag" -> postgresTag),
    healthCheck = ContainerHealthCheck.postgreSqlProtocolCheck(containerPort, user, password),
  )
}
