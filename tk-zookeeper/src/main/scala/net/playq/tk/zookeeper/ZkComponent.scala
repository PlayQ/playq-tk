package net.playq.tk.zookeeper

import distage.{Id, Lifecycle}
import izumi.functional.bio.BlockingIO2
import net.playq.tk.zookeeper.config.ZookeeperConfig
import org.apache.curator.RetryPolicy
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}

import scala.util.chaining.scalaUtilChainingOps

final case class ZkComponent[F[+_, +_]: BlockingIO2](client: CuratorFramework) {
  def withZkRequest[A](f: CuratorFramework => A): F[Throwable, A] = {
    BlockingIO2[F].syncBlocking(f(client))
  }
}

object ZkComponent {

  final class Impl[F[+_, +_]](
    zkCfg: ZookeeperConfig,
    zkRetryPolicy: RetryPolicy @Id("zookeeper-client"),
  )(implicit F: BlockingIO2[F]
  ) extends Lifecycle.MakePair[F[Throwable, ?], ZkComponent[F]](
      F.syncBlocking {
        val client = CuratorFrameworkFactory
          .newClient(zkCfg.url, zkRetryPolicy)
          .tap(_.start())
        (ZkComponent(client), F.syncBlocking(client.close()))
      }
    )

}
