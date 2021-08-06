package d4s.health

import d4s.DynamoClient
import izumi.functional.bio.{Exit, Panic2}
import net.playq.tk.health.{HealthChecker, TkHealthCheckStatus, TkHealthState}

final class DynamoDBHealthChecker[F[+_, +_]: Panic2](client: DynamoClient[F]) extends HealthChecker[F] {
  def healthCheck(): F[Throwable, Set[TkHealthCheckStatus]] = {
    client
      .raw(_.listTables)
      .sandboxExit.map {
        case _: Exit.Success[_] =>
          Set(TkHealthCheckStatus("dynamodb.session", TkHealthState.OK))
        case _: Exit.Error[_] =>
          Set(TkHealthCheckStatus("dynamodb.session", TkHealthState.DEFUNCT))
        case _: Exit.Termination =>
          Set(TkHealthCheckStatus("dynamodb.session", TkHealthState.UNKNOWN))
        case _: Exit.Interruption =>
          Set(TkHealthCheckStatus("dynamodb.session", TkHealthState.UNKNOWN))
      }
  }
}
