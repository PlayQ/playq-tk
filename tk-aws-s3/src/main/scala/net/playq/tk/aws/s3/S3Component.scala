package net.playq.tk.aws.s3

import izumi.functional.bio.{BlockingIO2, F, IO2, Panic2}
import net.playq.metrics.Metrics
import net.playq.tk.metrics.{MacroMetricS3Meter, MacroMetricS3Timer}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner

trait S3Component[F[+_, +_]] {
  def presign[E, A](
    metric: String
  )(f: S3Presigner => A
  )(implicit saveCounter: MacroMetricS3Meter[metric.type],
    saveTimer: MacroMetricS3Timer[metric.type],
  ): F[Throwable, A]

  private[s3] def rawRequestF[E, A](
    metric: String
  )(f: S3Client => F[E, A]
  )(implicit saveCounter: MacroMetricS3Meter[metric.type],
    saveTimer: MacroMetricS3Timer[metric.type],
  ): F[E, A]

  def rawRequest[A](
    metric: String
  )(f: S3Client => A
  )(implicit
    saveCounter: MacroMetricS3Meter[metric.type],
    saveTimer: MacroMetricS3Timer[metric.type],
  ): F[Throwable, A]

  def getBucketRegion(bucketName: String): F[Throwable, String]
}

object S3Component {

  final class Impl[F[+_, +_]: IO2: BlockingIO2](
    metrics: Metrics[F],
    client: S3Client,
    presigner: S3Presigner,
  ) extends S3Component[F] {

    override def presign[E, A](
      metric: String
    )(f: S3Presigner => A
    )(implicit
      saveCounter: MacroMetricS3Meter[metric.type],
      saveTimer: MacroMetricS3Timer[metric.type],
    ): F[Throwable, A] = {
      F.shiftBlocking {
        metrics.withTimer(metric) {
          F.syncThrowable(f(presigner)).tapError(_ => metrics.mark(metric))
        }
      }
    }

    override def rawRequestF[E, A](
      metric: String
    )(f: S3Client => F[E, A]
    )(implicit
      saveCounter: MacroMetricS3Meter[metric.type],
      saveTimer: MacroMetricS3Timer[metric.type],
    ): F[E, A] = {
      F.shiftBlocking {
        metrics.withTimer(metric) {
          f(client).tapError(_ => metrics.mark(metric))
        }
      }
    }

    override def rawRequest[A](
      metric: String
    )(f: S3Client => A
    )(implicit
      saveCounter: MacroMetricS3Meter[metric.type],
      saveTimer: MacroMetricS3Timer[metric.type],
    ): F[Throwable, A] = {
      rawRequestF(metric)(F syncThrowable f(_))
    }

    override def getBucketRegion(bucketName: String): F[Throwable, String] = {
      for {
        rsp <- rawRequest("get-bucket-region")(_.getBucketLocation(GetBucketLocationRequest.builder().bucket(bucketName).build()))
      } yield {
        Option(rsp.locationConstraint.toString)
          .filter(s => s != "null" && s.nonEmpty)
          .fold(Region.US_EAST_1)(Region.of)
          .toString
      }
    }

  }

  // format: off
  final class Dummy[F[+_, +_]: Panic2] extends S3Component[F] {
    private val error = F.terminate(new UnsupportedOperationException("Dummy S3 component in use. Must be a bug."))
    override def presign[E, A](metric: String)(f: S3Presigner => A)(implicit saveCounter: MacroMetricS3Meter[metric.type], saveTimer: MacroMetricS3Timer[metric.type]): F[Throwable, A] = error
    override def rawRequestF[E, A](metric: String)(f: S3Client => F[E, A])(implicit saveCounter: MacroMetricS3Meter[metric.type], saveTimer: MacroMetricS3Timer[metric.type]): F[E, A] = error
    override def rawRequest[A](metric: String)(f: S3Client => A)(implicit saveCounter: MacroMetricS3Meter[metric.type], saveTimer: MacroMetricS3Timer[metric.type]): F[Throwable, A] = error
    override def getBucketRegion(bucketName: String): F[Nothing, String] = F.pure("dummy")
  }
  // format: on

}
