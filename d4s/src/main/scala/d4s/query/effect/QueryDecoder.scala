package d4s.query.effect

import d4s.exceptions.DynamoException
import d4s.exceptions.DynamoException.DecoderException
import izumi.functional.bio.{F, IO2}

trait QueryDecoder[-Rsp, +Dec] {
  self =>
  def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, Rsp]): F[DynamoException, Dec]

  @inline final def map[C](f: Dec => C): QueryDecoder[Rsp, C] = new QueryDecoder[Rsp, C] {
    override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, Rsp]): F[DynamoException, C] = {
      self.decode(rsp).map(f)
    }
  }

  @inline final def flatMap[C](f: FnIO[DynamoException, Dec, C]): QueryDecoder[Rsp, C] = new QueryDecoder[Rsp, C] {
    override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, Rsp]): F[DynamoException, C] = {
      self.decode(rsp).flatMap(f.apply[F])
    }
  }

  @inline final def contramap[R](f: R => Rsp): QueryDecoder[R, Dec] = new QueryDecoder[R, Dec] {
    override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, R]): F[DynamoException, Dec] =
      self.decode(rsp.map(f))
  }
}

object QueryDecoder {
  @inline def pure[A]: QueryDecoder[A, A] = new QueryDecoder[A, A] {
    override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, A]): F[DynamoException, A] = F.fromEither(rsp)
  }
  @inline def syncThrowable[A, B](f: A => B): QueryDecoder[A, B] = new QueryDecoder[A, B] {
    override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, A]): F[DynamoException, B] = {
      F.fromEither(rsp).flatMap(a => F.syncThrowable(f(a)).leftMap(DecoderException.apply))
    }
  }
  @inline def fromEither[A, B](f: A => Either[Throwable, B]): QueryDecoder[A, B] = new QueryDecoder[A, B] {
    override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, A]): F[DynamoException, B] = {
      F.fromEither(rsp.flatMap(f)).leftMap(DecoderException.apply)
    }
  }
}
