package net.playq.tk.aws.sqs

import izumi.functional.bio.Exit.{Error, Interruption, Termination}
import izumi.functional.bio.{F, IO2}
import logstage.LogIO2
import net.playq.tk.health.{HealthChecker, TkHealthCheckStatus, TkHealthState}
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest

final class SQSHealthChecker[F[+_, +_]: IO2](
  sqsComponent: SQSComponent[F],
  queues: Set[SQSQueueId],
  log: LogIO2[F],
) extends HealthChecker[F] {

  private[this] val queuesWithStaticRegions: Set[SQSQueueId] = queues.filterNot(_.dynamicRegion)

  private[this] def sandboxRequest[A](name: String)(f: => F[Throwable, A]): F[Throwable, TkHealthCheckStatus] = {
    f.as(TkHealthState.OK)
      .sandbox.catchAll {
        case Error(error, trace) =>
          log.error(s"Error while health checking AWS with exception: $error $trace") *>
          F.pure(TkHealthState.DEFUNCT)
        case Termination(compoundException, allExceptions, trace) =>
          log.crit(s"Error while health checking AWS with exception: $compoundException, other exceptions: $allExceptions, trace: $trace") *>
          F.pure(TkHealthState.DEFUNCT)
        case Interruption(compoundException, trace) =>
          log.crit(s"Error while health checking AWS with interruption: $compoundException, trace: $trace") *>
          F.pure(TkHealthState.DEFUNCT)
      }.map(TkHealthCheckStatus(s"aws-$name", _))
  }

  private[this] def queueHealthCheck(queueId: SQSQueueId): F[Throwable, TkHealthCheckStatus] = {
    sandboxRequest(s"sqs-${queueId.queueName}") {
      sqsComponent.rawClientRequest("health")(_.getQueueUrl(GetQueueUrlRequest.builder.queueName(queueId.queueName).build))
    }
  }

  override def healthCheck(): F[Throwable, Set[TkHealthCheckStatus]] = {
    F.traverse(queuesWithStaticRegions)(queueHealthCheck).map(_.toSet)
  }
}
