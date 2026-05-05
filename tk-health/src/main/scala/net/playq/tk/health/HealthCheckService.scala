package net.playq.tk.health

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Applicative2, F, Fork2, IO2, Primitives2, Ref2, Temporal2}
import logstage.LogIO2
import net.playq.tk.control.Forever
import net.playq.tk.health.config.HealthCheckServiceConfig

trait HealthCheckService[F[_, _]] {
  def healthSummary: F[Nothing, TkHealthSummary]
}

object HealthCheckService {
  def empty[F[+_, +_]: Applicative2]: HealthCheckService[F] = new HealthCheckService[F] {
    override def healthSummary: F[Nothing, TkHealthSummary] = F.pure(TkHealthSummary(Nil, TkHealthState.OK))
  }

  final class AsyncChecker[F[+_, +_]: IO2: Fork2: Primitives2: Temporal2](
    healthCheckers: Set[HealthChecker[F]],
    config: HealthCheckServiceConfig,
  )(implicit log: LogIO2[F]
  ) extends Lifecycle.Of[F[Throwable, _], HealthCheckService[F]](
      for {
        // we assume that health on startup is OK
        ref <- Lifecycle.liftF[F[Throwable, _], Ref2[F, TkHealthSummary]](F.mkRef(TkHealthSummary(Nil, TkHealthState.OK)))
        // forever loop with configurable sleep to update health state
        _ <- Lifecycle.fork {
          Forever.loopForeverWithLog("Health Checker") {
            for {
              summary <- {
                for {
                  checks <- F.traverse(healthCheckers)(_.healthCheck()).map(_.flatten.sortBy(_.name))
                  overall = checks match {
                    case health if health.forall(_.status == TkHealthState.OK)      => TkHealthState.OK
                    case health if health.exists(_.status == TkHealthState.DEFUNCT) => TkHealthState.DEFUNCT
                    case _                                                          => TkHealthState.UNKNOWN
                  }
                } yield TkHealthSummary(checks, overall)
              }.sandbox.catchAll {
                err =>
                  log.error(s"Health check failed: $err").as(TkHealthSummary(Nil, TkHealthState.DEFUNCT))
              }
              _ <- ref.set(summary)
              _ <- F.sleep(config.updateInterval)
            } yield ()
          }
        }
      } yield {
        new HealthCheckService[F] {
          override def healthSummary: F[Nothing, TkHealthSummary] = ref.get
        }
      }
    )
}
