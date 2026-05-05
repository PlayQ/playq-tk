package net.playq.tk.aws.sqs

import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.functional.bio.{Async2, BlockingIO2, F}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import net.playq.tk.aws.config.AwsCredentialsConfig
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.metrics.Metrics

import java.net.URI

trait SQSComponentFactory[F[+_, +_]] {
  def mkComponent(region: Option[String]): Lifecycle[F[Throwable, _], SQSComponent[F]]
}

object SQSComponentFactory {

  final class Impl[F[+_, +_]: Async2: BlockingIO2](
    metrics: Metrics[F],
    sqsConfig: SQSConfig,
    portCheck: PortCheck,
    credentialsConfig: AwsCredentialsConfig,
  ) extends SQSComponentFactory[F]
    with IntegrationCheck[F[Throwable, _]] {

    override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
      sqsConfig.getEndpoint.fold(
        ResourceCheck.Success(): ResourceCheck
      )(url => portCheck.checkUrl(URI.create(url).toURL, "SQS Client"))
    }

    override def mkComponent(region: Option[String]): Lifecycle[F[Throwable, _], SQSComponent[F]] = {
      val configuredRegion = region.orElse(credentialsConfig.region)
      SQSComponent.resource(metrics, sqsConfig, configuredRegion, credentialsConfig)
    }
  }

}
