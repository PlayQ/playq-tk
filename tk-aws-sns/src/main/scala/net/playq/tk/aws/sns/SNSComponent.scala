package net.playq.tk.aws.sns

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, F}
import net.playq.tk.aws.config.AwsCredentialsConfig
import net.playq.tk.aws.sns.config.SNSConfig
import net.playq.tk.metrics.{MacroMetricSNSMeter, MacroMetricSNSTimer, Metrics}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient

import java.net.URI
import scala.util.chaining.*

trait SNSComponent[F[+_, +_]] {
  def rawRequest[T](
    metric: String
  )(f: SnsClient => T
  )(implicit
    saveCounter: MacroMetricSNSMeter[metric.type],
    saveTimer: MacroMetricSNSTimer[metric.type],
  ): F[Throwable, T]
}

object SNSComponent {
  def resource[F[+_, +_]: Async2](
    region: Option[String],
    credentialsConfig: AwsCredentialsConfig,
    snsConfig: SNSConfig,
    metrics: Metrics[F],
  ): Lifecycle[F[Throwable, _], SNSComponent[F]] = {
    for {
      client <- Lifecycle.fromAutoCloseable(
        F.syncThrowable(
          SnsClient
            .builder()
            .pipe(builder => region.fold(builder)(builder `region` Region.of(_)))
            .pipe(builder => credentialsConfig.provider.fold(builder)(builder.credentialsProvider))
            .pipe(builder => snsConfig.endpoint.fold(builder)(builder `endpointOverride` URI.create(_)))
            .build()
        )
      )
    } yield new SNSComponent[F] {
      override def rawRequest[T](
        metric: String
      )(f: SnsClient => T
      )(implicit
        saveCounter: MacroMetricSNSMeter[metric.type],
        saveTimer: MacroMetricSNSTimer[metric.type],
      ): F[Throwable, T] = {
        metrics.timerAnd(metric) {
          F.syncThrowable(f(client)).tapError(_ => metrics.mark(metric)(using saveCounter))
        }(using saveTimer)
      }
    }
  }
}
