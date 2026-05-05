package net.playq.tk.postgres.docker

object PostgresDockerDefault extends PostgresDockerBase {
  override def postgresTag: String = "default"
}
