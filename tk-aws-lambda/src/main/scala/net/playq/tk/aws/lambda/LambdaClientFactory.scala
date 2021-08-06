package net.playq.tk.aws.lambda

import izumi.functional.bio.IO2
import logstage.LogIO2
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.aws.lambda.config.LambdaConfig

trait LambdaClientFactory[F[_, _]] {
  def serviceBased(serviceName: ServiceName): LambdaClient[F]
}

object LambdaClientFactory {
  final class Dummy[F[+_, +_]: IO2] extends LambdaClientFactory[F] {
    override def serviceBased(serviceName: ServiceName): LambdaClient[F] = {
      new LambdaClient.Dummy()
    }
  }

  final class Impl[F[+_, +_]: IO2](
    component: LambdaComponent[F],
    lambdaConfig: LambdaConfig,
    namespace: AwsNameSpace,
    logger: LogIO2[F],
  ) extends LambdaClientFactory[F] {
    override def serviceBased(serviceName: ServiceName): LambdaClient[F] = {
      new LambdaClient.Impl[F](component, lambdaConfig, namespace, logger, serviceName)
    }
  }

}
