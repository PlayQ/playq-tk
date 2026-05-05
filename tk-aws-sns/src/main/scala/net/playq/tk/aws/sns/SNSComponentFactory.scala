package net.playq.tk.aws.sns

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.Async2
import net.playq.tk.aws.config.AwsCredentialsConfig
import net.playq.tk.aws.sns.config.SNSConfig
import net.playq.tk.metrics.Metrics

trait SNSComponentFactory[F[+_, +_]] {
  def mkComponent(region: Option[String]): Lifecycle[F[Throwable, _], SNSComponent[F]]
}

object SNSComponentFactory {
  final case class Impl[F[+_, +_]: Async2](
    credentialsConfig: AwsCredentialsConfig,
    snsConfig: SNSConfig,
    metrics: Metrics[F],
  ) extends SNSComponentFactory[F] {
    override def mkComponent(region: Option[String]): Lifecycle[F[Throwable, _], SNSComponent[F]] = {
      val confRegion = region.orElse(credentialsConfig.region)
      SNSComponent.resource(confRegion, credentialsConfig, snsConfig, metrics)
    }
  }
}
