package d4s.query.effect

import d4s.exceptions.DynamoException
import izumi.functional.bio.{IO2, Temporal2}

trait QueryStream[-DQ[_], B] {
  self =>
  def stream[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F], wrapper: QueryStreamLogger[F])(query: DQ[Dec]): fs2.Stream[F[DynamoException, _], B]
}
