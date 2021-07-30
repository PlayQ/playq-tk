package net.playq.tk.health

trait HealthChecker[F[_, _]] {
  def healthCheck(): F[Throwable, Set[TgHealthCheckStatus]]
}
