package net.playq.tk.aws.s3.health

import izumi.functional.bio.Exit.{Error, Termination}
import izumi.functional.bio.{F, IO2}
import logstage.LogIO2
import net.playq.tk.aws.s3.{S3Bucket, S3BucketId}
import net.playq.tk.health.{HealthChecker, TkHealthCheckStatus, TkHealthState}

final class S3HealthChecker[F[+_, +_]: IO2](
  healthCheckers: Set[S3Bucket[F, S3BucketId]],
  logger: LogIO2[F],
) extends HealthChecker[F] {

  private[this] lazy val bucketForHealth: Set[S3Bucket[F, S3BucketId]] = {
    healthCheckers.filter {
      _.bucket match {
        case f: S3BucketId.Managed => f.serviceCreatesBucket
        case _                     => false
      }
    }
  }

  private[this] def sandboxRequest[A](name: String)(f: F[Throwable, A]): F[Throwable, TkHealthCheckStatus] = {
    f.sandbox.tapError {
      case Termination(exception, allExceptions, trace) =>
        logger.crit(s"Error while health checking AWS with exception: $exception, other exceptions: $allExceptions, trace: $trace")
      case Error(exception, trace) =>
        logger.error(s"Error while health checking AWS with exception: $exception $trace")
    }
      .redeemPure(err = _ => TkHealthState.DEFUNCT, succ = _ => TkHealthState.OK)
      .map(TkHealthCheckStatus(name, _))
  }

  override def healthCheck(): F[Throwable, Set[TkHealthCheckStatus]] = {
    F.traverse(bucketForHealth)(b => sandboxRequest(s"aws-s3-${b.bucketName}")(b.healthProbe)).map(_.toSet)
  }
}
