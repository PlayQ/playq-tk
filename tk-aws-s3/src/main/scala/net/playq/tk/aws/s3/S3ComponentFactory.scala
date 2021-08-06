package net.playq.tk.aws.s3

import distage.Lifecycle2
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BlockingIO2, F, IO2, Panic2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import net.playq.tk.metrics.Metrics
import net.playq.tk.aws.config.LocalTestCredentials
import net.playq.tk.aws.s3.config.S3Config
import software.amazon.awssdk.auth.credentials.{AwsCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.{S3Client, S3Configuration}

import java.net.URI
import scala.jdk.DurationConverters.ScalaDurationOps
import scala.util.chaining.scalaUtilChainingOps

trait S3ComponentFactory[F[+_, +_]] {
  def mkClient(region: Option[String], credentials: Option[AwsCredentials]): Lifecycle2[F, Throwable, S3Component[F]]
}

object S3ComponentFactory {

  final class Impl[F[+_, +_]: IO2: BlockingIO2](
    s3Conf: S3Config,
    portCheck: PortCheck,
    localCredentials: LocalTestCredentials,
    metrics: Metrics[F],
  ) extends S3ComponentFactory[F]
    with IntegrationCheck[F[Throwable, ?]] {

    override def resourcesAvailable(): F[Nothing, ResourceCheck] = F.sync {
      s3Conf.getEndpoint.fold(
        ResourceCheck.Success(): ResourceCheck
      )(url => portCheck.checkUrl(URI.create(url).toURL, "S3Client"))
    }

    override def mkClient(region: Option[String], credentials: Option[AwsCredentials]): Lifecycle2[F, Throwable, S3Component[F]] = {
      val configuredRegion = region.orElse(s3Conf.getRegion)
      makeS3Component(configuredRegion, credentials.map(StaticCredentialsProvider.create))
    }

    private[this] def makeS3Component(region: => Option[String], credentialsProvider: => Option[StaticCredentialsProvider]): Lifecycle2[F, Throwable, S3Component[F]] = {
      Lifecycle.makePair(F.syncThrowable {
        val (client, presigner) = makeClientsSync(region, credentialsProvider)
        val release             = F.sync { client.close(); presigner.close() }
        (new S3Component.Impl[F](metrics, client, presigner), release)
      })
    }

    private[this] def makeClientsSync(region: Option[String], credentialsProvider: Option[StaticCredentialsProvider]): (S3Client, S3Presigner) = {
      val s3Config = S3Configuration.builder.pathStyleAccessEnabled(true)
      val client = S3Client
        .builder()
        .serviceConfiguration(s3Config.build())
        .pipe(builder => region.fold(builder)(builder region Region.of(_)))
        .pipe(builder => credentialsProvider.fold(builder)(builder.credentialsProvider))
        .pipe(builder => s3Conf.getEndpoint.fold(builder)(builder endpointOverride URI.create(_) credentialsProvider localCredentials.get))
        .pipe(setTimeouts)
        .build()
      val presigner = S3Presigner
        .builder()
        .serviceConfiguration(s3Config.checksumValidationEnabled(false).build())
        .pipe(builder => region.fold(builder)(builder region Region.of(_)))
        .pipe(builder => credentialsProvider.fold(builder)(builder.credentialsProvider))
        .pipe(builder => s3Conf.getEndpoint.fold(builder)(builder endpointOverride URI.create(_) credentialsProvider localCredentials.get))
        .build()
      client -> presigner
    }

    private[this] def setTimeouts[B <: AwsClientBuilder[B, C], C](builder: AwsClientBuilder[B, C]): AwsClientBuilder[B, C] = {
      val config = ClientOverrideConfiguration.builder().apiCallAttemptTimeout(s3Conf.connectionTimeout.toJava).build()
      builder.overrideConfiguration(config)
    }

  }

  final class Empty[F[+_, +_]: Panic2] extends S3ComponentFactory[F] {
    override def mkClient(region: Option[String], credentials: Option[AwsCredentials]): Lifecycle2[F, Throwable, S3Component[F]] = {
      Lifecycle.liftF(F.pure(new S3Component.Dummy[F]))
    }
  }

}
