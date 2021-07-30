package net.playq.tk.health

import izumi.functional.bio.{Async2, F}
import izumi.fundamentals.platform.language.open

@open class HealthCheckService[F[+_, +_]: Async2](
  healthCheckers: Set[HealthChecker[F]]
) {

  def allHealthChecks(): F[Throwable, Vector[TgHealthCheckStatus]] = {
    F.parTraverse(healthCheckers)(_.healthCheck()).map(_.flatten.toVector)
  }

  def withOverall(overallName: Option[String] = None): F[Throwable, List[TgHealthCheckStatus]] = {
    for {
      all          <- allHealthChecks()
      overallStatus = HealthCheckService.healthSummary(all)
      res           = all :+ TgHealthCheckStatus(overallName.getOrElse(HealthCheckService.overallHealthLabel), overallStatus)
    } yield res.toList
  }
}

object HealthCheckService {
  private final val overallHealthLabel = "overall_health"

  def healthSummary(health: Seq[TgHealthCheckStatus]): TgHealthState = {
    if (health.forall(_.status == TgHealthState.OK)) {
      TgHealthState.OK
    } else if (health.exists(_.status == TgHealthState.DEFUNCT)) {
      TgHealthState.DEFUNCT
    } else {
      TgHealthState.UNKNOWN
    }
  }
}
