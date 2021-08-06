package net.playq.tk.aws.sts

import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BlockingIO2, F, IO2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import logstage.LogIO2
import net.playq.tk.metrics.Metrics
import net.playq.tk.aws.config.LocalTestCredentials
import net.playq.tk.aws.sts.config.STSConfig
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient

import java.net.URI
import scala.util.chaining._

trait STSComponent[F[_, _]] {
  def mkClient(region: Option[String]): Lifecycle[F[Throwable, ?], STSClient[F]]
}

object STSComponent {

  final class Impl[F[+_, +_]: IO2: BlockingIO2](
    config: STSConfig,
    portCheck: PortCheck,
    localCredentials: LocalTestCredentials,
    metrics: Metrics[F],
  )(implicit
    log: LogIO2[F]
  ) extends STSComponent[F]
    with IntegrationCheck[F[Throwable, ?]] {

    override def mkClient(region: Option[String]): Lifecycle[F[Throwable, ?], STSClient[F]] = {
      val configuredRegion = region.orElse(config.getRegion)
      Lifecycle
        .fromAutoCloseable(F.syncThrowable(makeClientSync(configuredRegion)))
        .map(new STSClient.Impl[F](_, metrics, config))
    }

    private[this] def makeClientSync(region: Option[String]): StsClient = {
      StsClient
        .builder()
        .pipe(builder => config.getEndpoint.fold(builder)(builder endpointOverride URI.create(_) credentialsProvider localCredentials.get))
        .pipe(builder => region.fold(builder)(builder region Region.of(_)))
        .build()
    }

    override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
      config.getEndpoint.fold(
        ResourceCheck.Success(): ResourceCheck
      )(url => portCheck.checkUrl(URI.create(url).toURL, "STSClient"))
    }
  }
}
