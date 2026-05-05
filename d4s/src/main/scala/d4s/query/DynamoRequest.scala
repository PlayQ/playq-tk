package d4s.query

import d4s.exceptions.DynamoException
import d4s.query.effect.QueryDecoder
import izumi.functional.bio.IO2

trait DynamoRequest[+Dec] {
  type SelfType[+_]

  type Rsp

  def decoder: QueryDecoder[Rsp, Dec]

  def decodeF[C1](f: QueryDecoder[Rsp, C1]): SelfType[C1]

  @inline final def map[C1](f: Dec => C1): SelfType[C1] = decodeF(decoder.map(f))

  @inline final def decode[C1](f: Rsp => C1): SelfType[C1]                                 = decodeF(QueryDecoder.syncThrowable(f))
  @inline final def decodeWith[C1](f: (Rsp, Dec) => C1): SelfType[C1]                      = decodeWithF(QueryDecoder.syncThrowable(f.tupled))
  @inline final def decodeWithF[D >: Dec, C1](f: QueryDecoder[(Rsp, D), C1]): SelfType[C1] = decodeF {
    new QueryDecoder[Rsp, C1] {
      override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, Rsp]): F[DynamoException, C1] = {
        for {
          d   <- decoder.decode[F](rsp)
          rspd = rsp.map(_ -> d)
          c1  <- f.decode[F](rspd)
        } yield c1

      }
    }
  }
}
