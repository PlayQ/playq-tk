package net.playq.tk.aws.lambda

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2}
import software.amazon.awssdk.services.lambda.{LambdaClient => AWSLambda}

trait LambdaComponent[F[_, _]] {
  def rawRequest[A](f: AWSLambda => A): F[Throwable, A]
}

object LambdaComponent {
  final class Impl[F[+_, +_]: IO2](
    val client: AWSLambda
  ) extends LambdaComponent[F] {
    override def rawRequest[A](f: AWSLambda => A): F[Throwable, A] = F.syncThrowable {
      f(client)
    }
  }

  final class Resource[F[+_, +_]: IO2]
    extends Lifecycle.Of[F[Throwable, _], LambdaComponent[F]](
      Lifecycle.fromAutoCloseable {
        F.syncThrowable(AWSLambda.builder().build())
      }.map(new Impl(_))
    )
}
