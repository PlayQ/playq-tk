package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.StandardAxis.Repo
import net.playq.tk.health.HealthChecker
import net.playq.tk.zookeeper.{ZkClient, ZkComponent}
import net.playq.tk.zookeeper.docker.ZookeeperDocker
import net.playq.tk.zookeeper.health.ZkHealthChecker
import org.apache.curator.RetryPolicy
import org.apache.curator.retry.RetryUntilElapsed
import zio.{IO, Task}

object ZookeeperPlugin extends PluginDef {
  include(ZookeeperPlugin.module[IO])
  include(ZookeeperDocker.module[Task]("zookeeper"))

  def module[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Repo.Prod)

    make[ZkHealthChecker[F]].fromResource[ZkHealthChecker.Impl[F]]
    many[HealthChecker[F]].weak[ZkHealthChecker[F]]

    make[ZkComponent[F]].fromResource[ZkComponent.Impl[F]]
    make[ZkClient[F]].from[ZkClient.Impl[F]]
    make[RetryPolicy].named("zookeeper-client").from {
      new RetryUntilElapsed(30000, 200)
      // new ExponentialBackoffRetry(1000, 3)
    }
  }
}
