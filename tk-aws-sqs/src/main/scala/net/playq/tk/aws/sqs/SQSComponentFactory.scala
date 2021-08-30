package net.playq.tk.aws.sqs

import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BlockingIO2, F, IO2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import net.playq.tk.metrics.Metrics
import net.playq.tk.aws.config.LocalTestCredentials
import net.playq.tk.aws.sqs.config.SQSConfig

import java.net.URI

trait SQSComponentFactory[F[+_, +_]] {
  def mkClient(region: Option[String]): Lifecycle[F[Throwable, _], SQSComponent[F]]
}

object SQSComponentFactory {

  final class Impl[F[+_, +_]: IO2: BlockingIO2](
    metrics: Metrics[F],
    sqsConfig: SQSConfig,
    portCheck: PortCheck,
    localCredentials: LocalTestCredentials,
  ) extends SQSComponentFactory[F]
    with IntegrationCheck[F[Throwable, _]] {

    override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
      sqsConfig.getEndpoint.fold(
        ResourceCheck.Success(): ResourceCheck
      )(url => portCheck.checkUrl(URI.create(url).toURL, "SQS Client"))
    }

    override def mkClient(region: Option[String]): Lifecycle[F[Throwable, _], SQSComponent[F]] = {
      val configuredRegion = region.orElse(sqsConfig.getRegion)
      SQSComponent.resource(metrics, sqsConfig, configuredRegion, localCredentials)
    }
  }

}
