package d4s.zio

import d4s.Dynamo.{DynamoMetric, DynamoQueryExecutor, DynamoStreamExecutor}
import d4s.exceptions.DynamoException.QueryException
import d4s.query.{DynamoQuery, DynamoStream, IsSafeQuery}
import d4s.zio.DynamoZioEnv.WithDynamo
import d4s.{Dynamo, Dynamo3}
import fs2.Stream
import izumi.functional.bio.Applicative2

open class DynamoZioEnv[F[-_, +_, +_]](
  withDynamoConnector: WithDynamo[F]
)(implicit F: Applicative2[F[Dynamo3[F], +_, +_]]
) extends Dynamo[F[Dynamo3[F], +_, +_]] {

  override def run: DynamoQueryExecutor[F[Dynamo3[F], +_, +_]] = {
    new DynamoQueryExecutor[F[Dynamo3[F], +_, +_]] {
      override def apply[DR[+d] <: DynamoQuery[DR, d]: IsSafeQuery, A](q: DR[A]): F[Dynamo3[F], QueryException, A] = {
        withDynamoConnector(_.run(q))
      }
    }
  }

  override def run(metric: DynamoMetric): DynamoQueryExecutor[F[Dynamo3[F], +_, +_]] = {
    new DynamoQueryExecutor[F[Dynamo3[F], +_, +_]] {
      override def apply[DR[+d] <: DynamoQuery[DR, d]: IsSafeQuery, A](q: DR[A]): F[Dynamo3[F], QueryException, A] = {
        withDynamoConnector(_.run(metric)(q))
      }
    }
  }

  override def stream: DynamoStreamExecutor[F[Dynamo3[F], +_, +_]] = {
    new DynamoStreamExecutor[F[Dynamo3[F], +_, +_]] {
      override def apply[DR[+d] <: DynamoStream[DR, d], A](q: DR[A]): Stream[F[Dynamo3[F], QueryException, _], A] = {
        Stream.force[F[Dynamo3[F], QueryException, _], A] {
          withDynamoConnector(c => F.pure(c.stream(q)))
        }
      }
    }
  }

  override def stream(metric: DynamoMetric): DynamoStreamExecutor[F[Dynamo3[F], +_, +_]] = {
    new DynamoStreamExecutor[F[Dynamo3[F], +_, +_]] {
      override def apply[DR[+d] <: DynamoStream[DR, d], A](q: DR[A]): Stream[F[Dynamo3[F], QueryException, _], A] = {
        Stream.force[F[Dynamo3[F], QueryException, _], A] {
          withDynamoConnector(c => F.pure(c.stream(metric)(q)))
        }
      }
    }
  }
}

object DynamoZioEnv {
  trait WithDynamo[F[-_, +_, +_]] {
    def apply[R, E, A](f: d4s.Dynamo3[F] => F[R, E, A]): F[Dynamo3[F] & R, E, A]
  }
}
