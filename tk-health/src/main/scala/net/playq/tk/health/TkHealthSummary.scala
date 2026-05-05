package net.playq.tk.health

final case class TkHealthSummary(healthChecks: List[TkHealthCheckStatus], summary: TkHealthState) {
  def withOverall(overallName: String = "overall_health"): List[TkHealthCheckStatus] = TkHealthCheckStatus(overallName, summary) :: healthChecks
}
