package net.playq.tk.kafka

import java.util.concurrent.TimeUnit.MILLISECONDS
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, BlockingIO2, F, IO2, Primitives2, Temporal2}
import logstage.LogIO2
import KafkaTopicZkLock.ZkLock
import net.playq.tk.kafka.config.ZookeeperLockConfig
import net.playq.tk.zookeeper.ZkComponent
import org.apache.curator.framework.recipes.locks.{InterProcessLock, InterProcessSemaphoreMutex}
import org.apache.kafka.common.TopicPartition

import scala.concurrent.duration._

trait KafkaTopicZkLock[F[_, _]] {
  def lockFor(topicPartition: TopicPartition): F[Nothing, ZkLock[F]]
}

object KafkaTopicZkLock {

  trait ZkLock[F[_, _]] {
    def acquire(timeout: FiniteDuration): F[Nothing, Option[ReleaseZkLock[F]]]
  }
  final class ReleaseZkLock[F[_, _]](
    val release: F[Nothing, Unit]
  )

  final class Impl[F[+_, +_]: IO2: Temporal2: BlockingIO2](
    zkComponent: ZkComponent[F],
    zookeeperLockConf: ZookeeperLockConfig,
    log: LogIO2[F],
  ) extends KafkaTopicZkLock[F] {

    override def lockFor(topicPartition: TopicPartition): F[Nothing, ZkLock[F]] = F.sync {
      val path  = s"${zookeeperLockConf.lockDir}/${topicPartition.topic}/${topicPartition.partition}"
      val mutex = new InterProcessSemaphoreMutex(zkComponent.client, path)
      newLock(topicPartition, path, mutex)
    }

    private[this] def newLock(topicPartition: TopicPartition, path: String, lock: InterProcessLock): ZkLock[F] =
      new ZkLock[F] {
        override def acquire(timeout: FiniteDuration): F[Nothing, Option[ReleaseZkLock[F]]] =
          F.shiftBlocking(for {
            thread <- F.sync(Thread.currentThread().getName)
            _      <- log.info(s"$thread locking on $topicPartition $path for $timeout")

            res <- F.syncThrowable {
              lock.acquire(timeout.toMillis, MILLISECONDS)
            }.redeem(
              exception => log.error(s"$thread failed with $exception when trying to acquire lock for $topicPartition $path, returning None").as(None),
              acquired =>
                if (acquired) log.info(s"$thread successfully acquired $topicPartition $path").as(Some(newReleaser(topicPartition, path, lock)))
                else log.info(s"$thread timed out by $timeout on acquiring $topicPartition $path").as(None),
            )
          } yield res)
      }

    private[this] def newReleaser(topicPartition: TopicPartition, path: String, lock: InterProcessLock): ReleaseZkLock[F] =
      new ReleaseZkLock(
        F.syncThrowable(if (lock.isAcquiredInThisProcess) lock.release())
          .tapError(error => log.crit(s"when releasing lock for $topicPartition at $path an unexpected $error occurred"))
          .retryOrElse(
            60.seconds,
            log.crit(s"Couldn't release lock for $topicPartition at $path for 60 seconds, giving up"),
          )
      )
  }

  final class Dummy[F[+_, +_]: Async2: Temporal2: Primitives2]
    extends Lifecycle.LiftF[F[Throwable, ?], KafkaTopicZkLock[F]](for {
      state <- F.mkRef(Map.empty[TopicPartition, scala.concurrent.Promise[Unit]])
    } yield {
      new KafkaTopicZkLock[F] {
        override def lockFor(topicPartition: TopicPartition): F[Nothing, ZkLock[F]] =
          F.pure(new ZkLock[F] {
            override def acquire(timeout: FiniteDuration): F[Nothing, Option[ReleaseZkLock[F]]] = {
              state.modify {
                locks =>
                  locks.get(topicPartition) match {
                    case Some(p) =>
                      val waitAndReplace =
                        F.fromFuture(_ => p.future).orTerminate
                          .timeout(timeout)
                          .flatMap(F.traverse(_) {
                            _ =>
                              val (p, lock) = newLock()
                              state.update_(_.updated(topicPartition, p)).as(lock)
                          })
                      waitAndReplace -> locks
                    case None =>
                      val (p, lock) = newLock()
                      F.pure(Some(lock)) -> locks.updated(topicPartition, p)
                  }
              }.flatten
            }
          })

        private[this] def newLock(): (scala.concurrent.Promise[Unit], ReleaseZkLock[F]) = {
          val p    = scala.concurrent.Promise[Unit]()
          val lock = new ReleaseZkLock[F](F.sync(p.success(())))
          (p, lock)
        }
      }
    })

}
