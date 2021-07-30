package net.playq.tk.aws.sqs

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BlockingIO2, F, IO2}
import net.playq.metrics.Metrics
import net.playq.tk.metrics.{MacroMetricSQSMeter, MacroMetricSQSTimer}
import net.playq.tk.aws.config.LocalTestCredentials
import net.playq.tk.aws.sqs.config.SQSConfig
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient

import java.net.URI
import scala.util.chaining._

trait SQSComponent[F[+_, +_]] {
  def rawClientRequest[A](
    metric: String
  )(f: SqsClient => A
  )(implicit saveCounter: MacroMetricSQSMeter[metric.type],
    saveTimer: MacroMetricSQSTimer[metric.type],
  ): F[Throwable, A]
}

object SQSComponent {

  def resource[F[+_, +_]: IO2: BlockingIO2](
    metrics: Metrics[F],
    sqsConfig: SQSConfig,
    region: Option[String],
    localCredentials: LocalTestCredentials,
  ): Lifecycle[F[Throwable, ?], SQSComponent[F]] = {
    for {
      client <- Lifecycle.fromAutoCloseable(F.syncThrowable {
        SqsClient
          .builder()
          .pipe(builder => region.fold(builder)(builder region Region.of(_)))
          .pipe(builder => sqsConfig.getEndpoint.fold(builder)(builder endpointOverride URI.create(_) credentialsProvider localCredentials.get))
          .build()
      })
    } yield new SQSComponent[F] {
      override def rawClientRequest[A](
        metric: String
      )(f: SqsClient => A
      )(implicit saveCounter: MacroMetricSQSMeter[metric.type],
        saveTimer: MacroMetricSQSTimer[metric.type],
      ): F[Throwable, A] = {
        F.shiftBlocking {
          metrics.withTimer(metric) {
            F.syncThrowable(f(client))
              .tapError(_ => metrics.mark(metric)(saveCounter))
          }(saveTimer)
        }
      }
    }
  }

}
