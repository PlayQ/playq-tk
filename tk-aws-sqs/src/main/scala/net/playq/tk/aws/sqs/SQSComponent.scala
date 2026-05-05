package net.playq.tk.aws.sqs

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, BlockingIO2, F}
import net.playq.tk.aws.config.AwsCredentialsConfig
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.metrics.{MacroMetricSQSMeter, MacroMetricSQSTimer, Metrics}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient

import java.net.URI
import scala.util.chaining.*

trait SQSComponent[F[+_, +_]] {
  def rawClientRequest[A](
    metric: String
  )(f: SqsClient => A
  )(implicit
    saveCounter: MacroMetricSQSMeter[metric.type],
    saveTimer: MacroMetricSQSTimer[metric.type],
  ): F[Throwable, A]
}

object SQSComponent {
  def resource[F[+_, +_]: Async2: BlockingIO2](
    metrics: Metrics[F],
    sqsConfig: SQSConfig,
    region: Option[String],
    credentialsConfig: AwsCredentialsConfig,
  ): Lifecycle[F[Throwable, _], SQSComponent[F]] = {
    for {
      client <- Lifecycle.fromAutoCloseable(F.syncThrowable {
        SqsClient
          .builder()
          .pipe(builder => region.fold(builder)(builder `region` Region.of(_)))
          .pipe(builder => credentialsConfig.provider.fold(builder)(builder.credentialsProvider))
          .pipe(builder => sqsConfig.getEndpoint.fold(builder)(builder `endpointOverride` URI.create(_)))
          .build()
      })
    } yield new SQSComponent[F] {
      override def rawClientRequest[A](
        metric: String
      )(f: SqsClient => A
      )(implicit
        saveCounter: MacroMetricSQSMeter[metric.type],
        saveTimer: MacroMetricSQSTimer[metric.type],
      ): F[Throwable, A] = F.shiftBlocking {
        metrics.timerAnd(metric) {
          F.syncThrowable(f(client)).tapError(_ => metrics.mark(metric)(using saveCounter))
        }(using saveTimer)
      }
    }
  }
}
