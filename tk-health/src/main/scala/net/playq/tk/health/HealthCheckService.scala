package net.playq.tk.health

import izumi.functional.bio.{Async2, F}
import izumi.fundamentals.platform.language.open

@open class HealthCheckService[F[+_, +_]: Async2](
  healthCheckers: Set[HealthChecker[F]]
) {

  def allHealthChecks(): F[Throwable, Vector[TkHealthCheckStatus]] = {
    F.parTraverse(healthCheckers)(_.healthCheck()).map(_.flatten.toVector)
  }

  def withOverall(overallName: Option[String] = None): F[Throwable, List[TkHealthCheckStatus]] = {
    for {
      all          <- allHealthChecks()
      overallStatus = HealthCheckService.healthSummary(all)
      res           = all :+ TkHealthCheckStatus(overallName.getOrElse(HealthCheckService.overallHealthLabel), overallStatus)
    } yield res.toList
  }
}

object HealthCheckService {
  private final val overallHealthLabel = "overall_health"

  def healthSummary(health: Seq[TkHealthCheckStatus]): TkHealthState = {
    if (health.forall(_.status == TkHealthState.OK)) {
      TkHealthState.OK
    } else if (health.exists(_.status == TkHealthState.DEFUNCT)) {
      TkHealthState.DEFUNCT
    } else {
      TkHealthState.UNKNOWN
    }
  }
}
