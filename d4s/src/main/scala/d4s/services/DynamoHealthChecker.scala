package d4s.services

import izumi.functional.bio.{Exit, Panic2}
import net.playq.tk.health.{HealthChecker, TkHealthCheckStatus, TkHealthState}

final class DynamoHealthChecker[F[+_, +_]: Panic2](client: DynamoClient[F]) extends HealthChecker[F] {
  def healthCheck(): F[Throwable, Set[TkHealthCheckStatus]] = {
    client
      .executeFuture(_.listTables)
      .sandboxExit.map {
        case _: Exit.Success[?] =>
          Set(TkHealthCheckStatus("dynamodb.session", TkHealthState.OK))
        case _: Exit.Error[?] =>
          Set(TkHealthCheckStatus("dynamodb.session", TkHealthState.DEFUNCT))
        case _: Exit.Termination =>
          Set(TkHealthCheckStatus("dynamodb.session", TkHealthState.UNKNOWN))
      }
  }
}
