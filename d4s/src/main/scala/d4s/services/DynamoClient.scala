package d4s.services

import izumi.functional.bio.{Async2, BlockingIO2, F}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import java.util.concurrent.CompletableFuture

trait DynamoClient[F[_, _]] {
  /** Lift a single blocking method of dynamo client */
  def executeFuture[A](f: DynamoDbAsyncClient => CompletableFuture[A]): F[Throwable, A]

  /** Lift a single blocking method of dynamo client */
  def execute[A](f: DynamoDbAsyncClient => F[Throwable, A]): F[Throwable, A]
}

object DynamoClient {
  def apply[F[_, _]: DynamoClient]: DynamoClient[F] = implicitly

  final class Impl[F[+_, +_]: Async2](
    blocking: BlockingIO2[F],
    dynamo: DynamoComponent,
  ) extends DynamoClient[F] {
    def executeFuture[A](f: DynamoDbAsyncClient => CompletableFuture[A]): F[Throwable, A] = {
      blocking.shiftBlocking(F.fromFutureJava(f(dynamo.client)))
    }
    def execute[A](f: DynamoDbAsyncClient => F[Throwable, A]): F[Throwable, A] = {
      blocking.shiftBlocking(f(dynamo.client))
    }
  }
}
