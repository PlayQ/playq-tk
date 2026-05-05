package net.playq.tk.zookeeper

import distage.{Id, Lifecycle}
import izumi.functional.bio.{BlockingIO2, F, IO2}
import net.playq.tk.zookeeper.config.ZookeeperConfig
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}

import scala.util.chaining.scalaUtilChainingOps

final case class ZkComponent[F[+_, +_]: IO2: BlockingIO2](client: CuratorFramework) {
  def withZkRequest[A](f: CuratorFramework => A): F[Throwable, A] = {
    F.shiftBlocking(F.syncThrowable(f(client)))
  }
}

object ZkComponent {

  final class Impl[F[+_, +_]: IO2: BlockingIO2](
    zkCfg: ZookeeperConfig,
    zkRetryPolicy: RetryPolicy @Id("zookeeper-client"),
  ) extends Lifecycle.MakePair[F[Throwable, _], ZkComponent[F]](
      F.syncBlocking {
        val client = CuratorFrameworkFactory
          .newClient(zkCfg.url, zkRetryPolicy)
          .tap(_.start())
        (ZkComponent(client), F.syncBlocking(client.close()))
      }
    )

}
