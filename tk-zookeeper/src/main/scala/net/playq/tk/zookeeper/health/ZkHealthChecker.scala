package net.playq.tk.zookeeper.health

import distage.Lifecycle
import izumi.functional.bio.{Exit, F, IO2, Primitives2}
import logstage.LogIO2
import net.playq.tk.health.{HealthChecker, TkHealthCheckStatus, TkHealthState}
import net.playq.tk.zookeeper.ZkComponent
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex

import java.util.UUID
import java.util.concurrent.TimeUnit

trait ZkHealthChecker[F[_, _]] extends HealthChecker[F]

object ZkHealthChecker {
  final class Impl[F[+_, +_]: IO2: Primitives2](
    component: ZkComponent[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF(
      for {
        lockRef <- F.mkRef(Option.empty[InterProcessSemaphoreMutex])
        lockName = UUID.randomUUID()
      } yield new ZkHealthChecker[F] {
        private[this] val testLockPath = s"/test/health/lock/$lockName"

        override def healthCheck(): F[Throwable, Set[TkHealthCheckStatus]] = {
          val healthCheckAction = for {
            lock <- lockRef.get.flatMap {
              case Some(value) => F.pure(value)
              case None        => F.syncThrowable(new InterProcessSemaphoreMutex(component.client, testLockPath)).tap(l => lockRef.set(Some(l)))
            }
            _ <- F.syncThrowable(lock.acquire(10L, TimeUnit.SECONDS)).flatMap {
              case a if a && lock.isAcquiredInThisProcess => F.syncThrowable(lock.release())
              case _                                      => F.fail(new RuntimeException("Lock was not acquired in 10s. No errors available."))
            }
          } yield ()

          healthCheckAction.sandboxExit.flatMap {
            case Exit.Success(_)             => F.pure(TkHealthState.OK)
            case Exit.Error(err, _)          => log.error(s"Zookeeper Health Check failed: $err").as(TkHealthState.DEFUNCT)
            case Exit.Termination(err, _, _) => log.crit(s"Zookeeper Health Check terminated: $err").as(TkHealthState.DEFUNCT)
          }.map(s => Set(TkHealthCheckStatus("zookeeper.session", s)))
        }
      }
    )
}
