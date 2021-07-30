package net.playq.tk.postgres.docker

import izumi.distage.docker.Docker.DockerReusePolicy

object PostgresDockerDefault extends PostgresDockerBase {
  override def config: PostgresDockerDefault.Config = super.config.copy(reuse = DockerReusePolicy.ReuseDisabled)
}
