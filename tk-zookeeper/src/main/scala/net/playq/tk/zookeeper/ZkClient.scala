package net.playq.tk.zookeeper

import izumi.fundamentals.platform.language.Quirks.*
import ZkClient.ZkClientOps

import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters.*

trait ZkClient[F[_, _]] {
  def getData(path: String): F[Throwable, String]
  def setData(path: String, data: String): F[Throwable, Unit]

  def create(path: String): F[Throwable, Unit]
  def delete(path: String): F[Throwable, Unit]

  def exists(path: String): F[Throwable, Boolean]
  def getChildren(path: String): F[Throwable, List[String]]

  def transact(ops: List[ZkClientOps]): F[Throwable, Unit]
}

object ZkClient {

  final class Impl[F[+_, +_]](
    zkComponent: ZkComponent[F]
  ) extends ZkClient[F] {

    override def getData(path: String): F[Throwable, String] = {
      zkComponent.withZkRequest(client => new String(client.getData.forPath(path)))
    }

    override def setData(path: String, data: String): F[Throwable, Unit] = {
      zkComponent.withZkRequest(_.setData().forPath(path, data.getBytes(UTF_8)).discard())
    }

    override def create(path: String): F[Throwable, Unit] = {
      zkComponent.withZkRequest(_.create().creatingParentContainersIfNeeded().forPath(path).discard())
    }

    override def delete(path: String): F[Throwable, Unit] = {
      zkComponent.withZkRequest(_.delete().forPath(path).discard())
    }

    override def exists(path: String): F[Throwable, Boolean] = {
      zkComponent.withZkRequest(_.checkExists().forPath(path) ne null)
    }

    override def getChildren(path: String): F[Throwable, List[String]] = {
      zkComponent.withZkRequest(_.getChildren.forPath(path).asScala.toList)
    }

    override def transact(ops: List[ZkClientOps]): F[Throwable, Unit] = {
      zkComponent.withZkRequest {
        client =>
          val curatorOps = ops.map {
            case ZkClientOps.CreateOp(path)      => client.transactionOp().create().forPath(path)
            case ZkClientOps.DeleteOp(path)      => client.transactionOp().delete().forPath(path)
            case ZkClientOps.WriteOp(path, data) => client.transactionOp().setData().forPath(path, data.getBytes(UTF_8))
          }

          client.transaction().forOperations(curatorOps.asJava).discard()
      }
    }
  }

  sealed trait ZkClientOps
  object ZkClientOps {
    final case class CreateOp(path: String) extends ZkClientOps
    final case class DeleteOp(path: String) extends ZkClientOps
    final case class WriteOp(path: String, data: String) extends ZkClientOps
  }

}
