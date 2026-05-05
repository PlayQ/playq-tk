package d4s.query

import d4s.exceptions.DynamoException
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryStream, QueryStreamLogger}
import fs2.Stream
import izumi.functional.bio.{F, IO2, Temporal2}

trait DynamoStream[DQ[+_], +Dec] extends DynamoRequest[Dec] {
  self: DQ[Dec] =>

  override type SelfType[+d] = DQ[d]

  def stream: QueryStream[DQ, Rsp]

  @inline final def execute[F[+_, +_]: IO2: Temporal2](
    ctx: QueryCtx[F],
    logger: QueryStreamLogger[F],
  ): Stream[F[DynamoException, _], Dec] = {
    stream
      .stream(ctx, logger)(this)
      .attempt
      .evalMap {
        case Right(v)                 => decoder.decode[F](Right(v))
        case Left(d: DynamoException) => decoder.decode[F](Left(d))
        case Left(t: Throwable)       => F.terminate(t)
      }
  }
}

object DynamoStream {
  final class Raw[Rsp0, +Dec](
    override val decoder: QueryDecoder[Rsp0, Dec],
    override val stream: QueryStream[DynamoStream.Raw[Rsp0, +_], Rsp0],
  ) extends DynamoStream[DynamoStream.Raw[Rsp0, +_], Dec] {
    override type Rsp = Rsp0
    override def decodeF[C1](f: QueryDecoder[Rsp, C1]): DynamoStream.Raw[Rsp, C1] = new DynamoStream.Raw[Rsp, C1](f, stream)
  }
  object Raw {
    @inline def apply[Rsp0, Dec](decoder: QueryDecoder[Rsp0, Dec], stream: QueryStream[DynamoStream.Raw[Rsp0, +_], Rsp0]): Raw[Rsp0, Dec] = {
      new Raw(decoder, stream)
    }
  }
}
