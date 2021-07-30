package net.playq.tk.zookeeper

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2, Monad2, Primitives2, Temporal2}
import logstage.LogIO2
import logstage.LogIO2.log
import net.playq.tk.concurrent.LoopTimer
import net.playq.tk.control.{Mutex2, RefF2}
import net.playq.tk.zookeeper.config.ZookeeperConfig
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry

import java.time.ZonedDateTime
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.concurrent.duration.Duration

trait SynchronizedAction[F[_, _]] {
  def synchronizedLoopAction[E](timer: LoopTimer[F], lockPath: String)(eff: ZonedDateTime => F[E, Unit]): F[E, Unit]
  def synchronized[E](lockPath: String)(eff: F[E, Unit]): F[E, Unit]
}

object SynchronizedAction {

  final class Dummy[F[+_, +_]: IO2: Primitives2]
    extends Lifecycle.LiftF(for {
      mutexMap <- RefF2.make(Map.empty[String, Mutex2[F]])
    } yield new SynchronizedAction[F] {

      override def synchronizedLoopAction[E](timer: LoopTimer[F], lockPath: String)(eff: ZonedDateTime => F[E, Unit]): F[E, Unit] = {
        for {
          mm  <- mutexMap.update(s => if (s.contains(lockPath)) F.pure(s) else Mutex2.make.map(s + lockPath.->(_)))
          res <- mm(lockPath).bracket(timer.poll().flatMap(eff))
        } yield res
      }
      override def synchronized[E](lockPath: String)(eff: F[E, Unit]): F[E, Unit] = {
        eff
      }
    })

  final class Empty[F[+_, +_]: Monad2] extends SynchronizedAction[F] {
    override def synchronizedLoopAction[E](timer: LoopTimer[F], lockPath: String)(eff: ZonedDateTime => F[E, Unit]): F[E, Unit] = {
      timer.poll().flatMap(eff)
    }
    override def synchronized[E](lockPath: String)(eff: F[E, Unit]): F[E, Unit] = {
      eff
    }
  }

  final class Zookeeper[F[+_, +_]: IO2: LogIO2](
    zkCfg: ZookeeperConfig
  ) extends Lifecycle.Self[F[Throwable, ?], Zookeeper[F]]
    with SynchronizedAction[F] {
    private[this] val mutexMap                 = new ConcurrentHashMap[String, InterProcessSemaphoreMutex]()
    private[this] val retryPolicy              = new ExponentialBackoffRetry(1000, 3) // retry policy differs from ZKComponent...
    private[this] val client: CuratorFramework = CuratorFrameworkFactory.newClient(zkCfg.url, retryPolicy)

    private[this] val lockAcquireTime = Duration(5, TimeUnit.SECONDS)

    override def acquire: F[Throwable, Unit] = F.sync(client.start())
    override def release: F[Throwable, Unit] = F.sync(client.close())

    override def synchronized[E](lockPath: String)(eff: F[E, Unit]): F[E, Unit] = {
      getLock(lockPath)
        .flatMap(lockAction(_)(eff))
    }

    override def synchronizedLoopAction[E](timer: LoopTimer[F], lockPath: String)(eff: ZonedDateTime => F[E, Unit]): F[E, Unit] = {
      for {
        lock <- getLock(lockPath)
        _    <- log.info(s"Next polling cycle $lockPath...")
        _    <- lockAction(lock)(timer.poll().flatMap(eff))
      } yield ()
    }

    private[this] def getLock(lockPath: String) = F.sync {
      mutexMap.compute(
        lockPath,
        (_, r: InterProcessSemaphoreMutex) => {
          Option(r) match {
            case Some(v) => v
            case _       => new InterProcessSemaphoreMutex(client, lockPath)
          }
        },
      )
    }

    private[this] def lockAction[E](lock: InterProcessSemaphoreMutex)(eff: F[E, Unit]) = {
      F.bracket(acquire = F.sync(lock.acquire(lockAcquireTime.toMillis, TimeUnit.MILLISECONDS)))(
        // release lock if was acquired then we will release
        release = F.when(_)(F.sync(if (lock.isAcquiredInThisProcess) lock.release()))
      ) {
        // release lock if was acquired then we process tick execution
        F.when(_)(log.info("Zk lock acquired") *> eff)
      }
    }
  }

}
