package net.playq.tk.aws.sts

import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.functional.bio.{BlockingIO2, F, IO2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import logstage.LogIO2
import net.playq.tk.aws.config.AwsCredentialsConfig
import net.playq.tk.aws.sts.config.STSConfig
import net.playq.tk.metrics.Metrics
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient

import java.net.URI
import scala.util.chaining.*

trait STSComponent[F[_, _]] {
  def mkClient(region: Option[String]): Lifecycle[F[Throwable, _], STSClient[F]]
}

object STSComponent {

  final class Impl[F[+_, +_]: IO2: BlockingIO2](
    config: STSConfig,
    portCheck: PortCheck,
    credentialsConfig: AwsCredentialsConfig,
    metrics: Metrics[F],
  )(implicit
    log: LogIO2[F]
  ) extends STSComponent[F]
    with IntegrationCheck[F[Throwable, _]] {

    override def mkClient(region: Option[String]): Lifecycle[F[Throwable, _], STSClient[F]] = {
      val configuredRegion = region.orElse(credentialsConfig.region)
      Lifecycle
        .fromAutoCloseable(F.syncThrowable(makeClientSync(configuredRegion)))
        .map(new STSClient.Impl[F](_, metrics, config))
    }

    private[this] def makeClientSync(region: Option[String]): StsClient = {
      StsClient
        .builder()
        .pipe(builder => region.fold(builder)(builder `region` Region.of(_)))
        .pipe(builder => credentialsConfig.provider.fold(builder)(builder.credentialsProvider))
        .pipe(builder => config.getEndpoint.fold(builder)(builder `endpointOverride` URI.create(_)))
        .build()
    }

    override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
      config.getEndpoint.fold(
        ResourceCheck.Success(): ResourceCheck
      )(url => portCheck.checkUrl(URI.create(url).toURL, "STSClient"))
    }
  }
}
