package net.playq.tk.aws.sts

import izumi.functional.bio.{BlockingIO2, Error2, F, IO2}
import logstage.LogIO2
import net.playq.tk.metrics.Metrics
import net.playq.tk.aws.sts.config.STSConfig
import net.playq.tk.metrics.{MacroMetricSTSMeter, MacroMetricSTSTimer}
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.GetSessionTokenRequest

trait STSClient[F[_, _]] {
  def getTemporaryCredentials: F[Throwable, AwsSessionCredentials]

  private[sts] def rawRequestF[E, A](
    metric: String
  )(f: StsClient => F[E, A]
  )(implicit
    saveCounter: MacroMetricSTSMeter[metric.type],
    saveTimer: MacroMetricSTSTimer[metric.type],
  ): F[E, A]

  def rawRequest[A](
    metric: String
  )(f: StsClient => A
  )(implicit
    saveCounter: MacroMetricSTSMeter[metric.type],
    saveTimer: MacroMetricSTSTimer[metric.type],
  ): F[Throwable, A]
}

object STSClient {
  final class Impl[F[+_, +_]: IO2: BlockingIO2](
    client: StsClient,
    metrics: Metrics[F],
    config: STSConfig,
  )(implicit
    log: LogIO2[F]
  ) extends STSClient[F] {

    override def rawRequestF[E, A](
      metric: String
    )(f: StsClient => F[E, A]
    )(implicit
      saveCounter: MacroMetricSTSMeter[metric.type],
      saveTimer: MacroMetricSTSTimer[metric.type],
    ): F[E, A] = {
      F.shiftBlocking {
        metrics.withTimer(metric) {
          f(client).catchAll {
            failure =>
              metrics.mark(metric)(saveCounter) *> F.fail(failure)
          }
        }(saveTimer)
      }
    }

    override def rawRequest[A](
      metric: String
    )(f: StsClient => A
    )(implicit
      saveCounter: MacroMetricSTSMeter[metric.type],
      saveTimer: MacroMetricSTSTimer[metric.type],
    ): F[Throwable, A] = {
      rawRequestF(metric)(c => F.syncThrowable(f(c)))
    }

    override def getTemporaryCredentials: F[Throwable, AwsSessionCredentials] =
      rawRequest("get-temp-creds") {
        client =>
          val req          = GetSessionTokenRequest.builder().durationSeconds(config.tokensExpiration.toSeconds.toInt).build()
          val sessionCreds = client.getSessionToken(req).credentials()
          AwsSessionCredentials.create(sessionCreds.accessKeyId(), sessionCreds.secretAccessKey, sessionCreds.sessionToken)
      }.logThrowable("GetSessionToken")
  }

  private implicit final class ThrowableSTSOps[F[+_, +_], A](private val f: F[Throwable, A]) extends AnyVal {
    def logThrowable(operation: String)(implicit F: Error2[F], log: LogIO2[F]): F[Throwable, A] = {
      f.tapError {
        failure =>
          log.error(s"STS: Got error during executing $operation. ${failure.getMessage -> "Failure"}.")
      }
    }
  }
}
