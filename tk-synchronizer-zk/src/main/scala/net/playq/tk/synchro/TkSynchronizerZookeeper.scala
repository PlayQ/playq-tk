package net.playq.tk.synchro

import izumi.functional.bio.retry.Scheduler2
import izumi.functional.bio.{BlockingIO2, F, IO2, Temporal2}
import logstage.LogIO2
import net.playq.tk.synchro.TkSynchronizer.SynchronizerLockReleaser
import net.playq.tk.zookeeper.ZkComponent
import net.playq.tk.zookeeper.health.ZkHealthChecker
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.MILLISECONDS
import scala.annotation.unused
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class TkSynchronizerZookeeper[F[+_, +_]: IO2: BlockingIO2: Scheduler2](
  zkComponent: ZkComponent[F],
  log: LogIO2[F],
  @unused health: ZkHealthChecker[F],
) extends TkSynchronizer[F] {
  private[this] val mutexMap = new ConcurrentHashMap[String, InterProcessSemaphoreMutex]()

  override def tryAcquire(lockPath: String, await: FiniteDuration): F[Nothing, Option[SynchronizerLockReleaser[F]]] = {
    F.shiftBlocking(for {
      lock   <- getLock(lockPath)
      thread <- F.sync(Thread.currentThread().getName)
      _      <- log.info(s"$thread locking on $lockPath with timeout $await")

      res <- F.syncThrowable {
        lock.acquire(await.toMillis, MILLISECONDS)
      }.redeem(
        exception => log.error(s"$thread failed with $exception when trying to acquire lock for $lockPath, returning None").as(None),
        acquired =>
          if (acquired) log.info(s"$thread successfully acquired $lockPath").as(Some(releaser(lockPath, lock)))
          else log.info(s"$thread timed out by $await on acquiring $lockPath").as(None),
      )
    } yield res)
  }

  private[this] def getLock(lockPath: String): F[Nothing, InterProcessSemaphoreMutex] = F.sync {
    mutexMap.compute(
      lockPath,
      (_, r: InterProcessSemaphoreMutex) => {
        Option(r) match {
          case Some(v) => v
          case _       => new InterProcessSemaphoreMutex(zkComponent.client, lockPath)
        }
      },
    )
  }

  private[this] def releaser(lockPath: String, lock: InterProcessSemaphoreMutex): SynchronizerLockReleaser[F] = {
    new SynchronizerLockReleaser[F] {
      override def release: F[Nothing, Unit] = {
        F.retryOrElseUntil {
          F.syncThrowable(if (lock.isAcquiredInThisProcess) lock.release())
            .tapError(error => log.crit(s"When releasing lock for $lockPath an unexpected $error occurred."))
        }(
          60.seconds,
          _ => log.crit(s"Couldn't release lock for $lockPath for 60 seconds, giving up"),
        )
      }
    }
  }
}
