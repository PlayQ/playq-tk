package net.playq.tk.aws.s3.health

import izumi.functional.bio.Exit.{Error, Interruption, Termination}
import izumi.functional.bio.{F, Panic2}
import logstage.LogIO2
import net.playq.tk.aws.s3.{S3BucketId, S3Component}
import net.playq.tk.health.{HealthChecker, TgHealthCheckStatus, TgHealthState}
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest

final class S3HealthChecker[F[+_, +_]: Panic2](
  client: S3Component[F],
  logger: LogIO2[F],
  buckets: => Set[S3BucketId],
) extends HealthChecker[F] {

  private[this] lazy val bucketForHealth: Set[S3BucketId.GenWithOverride] = {
    buckets.collect { case f: S3BucketId.GenWithOverride if f.serviceCreatesBucket => f }
  }

  private[this] def sandboxRequest[A](name: String)(f: F[Throwable, A]): F[Throwable, TgHealthCheckStatus] = {
    f.sandbox.tapError {
      case Termination(exception, allExceptions, trace) =>
        logger.crit(s"Error while health checking AWS with exception: $exception, other exceptions: $allExceptions, trace: $trace")
      case Error(exception, trace) =>
        logger.error(s"Error while health checking AWS with exception: $exception $trace")
      case Interruption(compoundException, trace) =>
        logger.crit(s"Interrupted while health checking AWS with exception: $compoundException, trace: $trace")
    }
      .catchAll(_ => F.pure(TgHealthState.DEFUNCT))
      .as(TgHealthCheckStatus(s"aws-$name", TgHealthState.OK))
  }

  private[this] def bucketHealth(bucket: S3BucketId): F[Throwable, TgHealthCheckStatus] = {
    sandboxRequest(s"s3-${bucket.bucketName}") {
      client.rawRequest("health-probe")(_.getBucketLocation(GetBucketLocationRequest.builder.bucket(bucket.bucketName).build)).void
    }
  }

  override def healthCheck(): F[Throwable, Set[TgHealthCheckStatus]] = {
    F.traverse(bucketForHealth)(bucketHealth).map(_.toSet)
  }
}
