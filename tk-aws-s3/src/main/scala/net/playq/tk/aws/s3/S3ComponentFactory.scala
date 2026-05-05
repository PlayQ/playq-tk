package net.playq.tk.aws.s3

import distage.Lifecycle2
import izumi.distage.model.definition.{Id, Lifecycle}
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.functional.bio.{Async2, BlockingIO2, F, Panic2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import net.playq.tk.aws.config.AwsCredentialsConfig
import net.playq.tk.aws.http.TkAwsHttpClient
import net.playq.tk.aws.s3.config.{S3Config, S3RetryConfig}
import net.playq.tk.metrics.Metrics
import software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsCredentialsProvider, DefaultCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.checksums.{RequestChecksumCalculation, ResponseChecksumValidation}
import software.amazon.awssdk.core.client.config.{ClientAsyncConfiguration, ClientOverrideConfiguration, SdkAdvancedAsyncClientOption}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3Configuration}

import java.net.URI
import java.util.concurrent.ThreadPoolExecutor
import scala.jdk.DurationConverters.ScalaDurationOps
import scala.util.chaining.scalaUtilChainingOps

trait S3ComponentFactory[F[+_, +_]] {
  /**
    * Creating S3Component with Adaptive-V2 retry policy.
    * This components should be used only with a single S3 Bucket.
    * Components reusing might cause incorrect retry strategy stats with Service slow down.
    */
  def mkComponent(
    region: Option[String],
    credentials: Option[AwsCredentials],
    retryConfig: S3RetryConfig,
    checksumValidation: Boolean = true,
  ): Lifecycle2[F, Throwable, S3Component[F]]
}

object S3ComponentFactory {

  final class Impl[F[+_, +_]: Async2: BlockingIO2](
    s3Conf: S3Config,
    portCheck: PortCheck,
    credentialsConfig: AwsCredentialsConfig,
    metrics: Metrics[F],
    blockingExecutor: ThreadPoolExecutor @Id("blockingIO"),
    httpClient: TkAwsHttpClient,
  ) extends S3ComponentFactory[F]
    with IntegrationCheck[F[Throwable, _]] {

    override def resourcesAvailable(): F[Nothing, ResourceCheck] = F.sync {
      s3Conf.getEndpoint.fold(
        ResourceCheck.Success(): ResourceCheck
      )(url => portCheck.checkUrl(URI.create(url).toURL, "S3Client"))
    }

    override def mkComponent(
      region: Option[String],
      credentials: Option[AwsCredentials],
      retryConfig: S3RetryConfig,
      checksumValidation: Boolean,
    ): Lifecycle2[F, Throwable, S3Component[F]] = {
      val configuredRegion    = region.orElse(credentialsConfig.region)
      val credentialsProvider = credentials
        .map(StaticCredentialsProvider.create)
        .orElse(credentialsConfig.provider)
        .getOrElse(DefaultCredentialsProvider.builder().build())
      makeS3Component(configuredRegion, credentialsProvider, retryConfig, checksumValidation)
    }

    private[this] def makeS3Component(
      region: Option[String],
      credentialsProvider: AwsCredentialsProvider,
      componentConfig: S3RetryConfig,
      checksumValidation: Boolean,
    ): Lifecycle2[F, Throwable, S3Component[F]] = {
      Lifecycle.makePair(F.syncThrowable {
        val credentials         = credentialsProvider.resolveCredentials()
        val (client, presigner) = makeClientsSync(region, credentialsProvider, componentConfig, checksumValidation)
        val release             = F.sync { client.close(); presigner.close() }
        (new S3Component.Impl[F](region, credentials, metrics, client, presigner, blockingExecutor), release)
      })
    }

    private[this] def makeClientsSync(
      region: Option[String],
      credentialsProvider: AwsCredentialsProvider,
      retryConfig: S3RetryConfig,
      checksumValidation: Boolean,
    ): (S3AsyncClient, S3Presigner) = {
      val serviceConfig = S3Configuration.builder.pathStyleAccessEnabled(true).build()
      val asyncConfig   = ClientAsyncConfiguration
        .builder()
        .advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, blockingExecutor)
        .build()
      val client: S3AsyncClient = S3AsyncClient
        .builder()
        .serviceConfiguration(serviceConfig)
        .credentialsProvider(credentialsProvider)
        .responseChecksumValidation(if (checksumValidation) ResponseChecksumValidation.WHEN_SUPPORTED else ResponseChecksumValidation.WHEN_REQUIRED)
        .requestChecksumCalculation(if (checksumValidation) RequestChecksumCalculation.WHEN_SUPPORTED else RequestChecksumCalculation.WHEN_REQUIRED)
        .asyncConfiguration(asyncConfig)
        .httpClient(httpClient.client)
        .pipe(builder => region.fold(builder)(builder `region` Region.of(_)))
        .pipe(builder => s3Conf.getEndpoint.fold(builder)(builder `endpointOverride` URI.create(_)))
        .pipe(setTimeouts(retryConfig))
        .build()
      val presigner: S3Presigner = S3Presigner
        .builder()
        .serviceConfiguration(serviceConfig)
        .credentialsProvider(credentialsProvider)
        .pipe(builder => region.fold(builder)(builder `region` Region.of(_)))
        .pipe(builder => s3Conf.getEndpoint.fold(builder)(builder `endpointOverride` URI.create(_)))
        .build()
      client -> presigner
    }

    private[this] def setTimeouts[B <: AwsClientBuilder[B, C], C](retryConfig: S3RetryConfig)(builder: AwsClientBuilder[B, C]): AwsClientBuilder[B, C] = {
      val config = ClientOverrideConfiguration
        .builder()
        .retryStrategy(retryConfig.retryStrategy)
        .apiCallAttemptTimeout(s3Conf.connectionTimeout.toJava)
        .build()
      builder.overrideConfiguration(config)
    }
  }

  final class Empty[F[+_, +_]: Panic2] extends S3ComponentFactory[F] {
    override def mkComponent(
      region: Option[String],
      credentials: Option[AwsCredentials],
      retryConfig: S3RetryConfig,
      checksumValidation: Boolean,
    ): Lifecycle2[F, Throwable, S3Component[F]] = {
      Lifecycle.liftF(F.pure(new S3Component.Dummy[F]))
    }
  }

}
