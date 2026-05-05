package d4s.query.effect

import izumi.functional.bio.{F, IO2}

trait FnIO[+E, -A, +B] {
  self =>
  def apply[F[+_, +_]: IO2](a: A): F[E, B]

  @inline final def map[C](f: B => C): FnIO[E, A, C] = new FnIO[E, A, C] {
    override def apply[F[+_, +_]: IO2](a: A): F[E, C] = self.apply(a).map(f.apply)
  }

  @inline final def flatMap[E1 >: E, C](f: FnIO[E1, B, C]): FnIO[E1, A, C] = new FnIO[E1, A, C] {
    override def apply[F[+_, +_]: IO2](a: A): F[E1, C] = self.apply(a).flatMap(b => f.apply[F](b))
  }
}

object FnIO {
  private[FnIO] type UnknownF[+_, +_]
  @inline final def apply[E, A, B](f: A => IO2[UnknownF] => UnknownF[E, B]): FnIO[E, A, B] = new FnIO[E, A, B] {
    override def apply[F[+_, +_]: IO2](a: A): F[E, B] = {
      val castedF = f.asInstanceOf[A => IO2[F] => F[E, B]]
      castedF(a)(IO2[F])
    }
  }
  @inline final def as[A, B](b: B): FnIO[Nothing, A, B] = new FnIO[Nothing, A, B] {
    override def apply[F[+_, +_]: IO2](a: A): F[Nothing, B] = F.pure(b)
  }
  @inline final def pure[A]: FnIO[Nothing, A, A] = new FnIO[Nothing, A, A] {
    override def apply[F[+_, +_]: IO2](a: A): F[Nothing, A] = F.pure(a)
  }
  @inline final def sync[A, B](f: A => B): FnIO[Nothing, A, B] = new FnIO[Nothing, A, B] {
    override def apply[F[+_, +_]: IO2](a: A): F[Nothing, B] = F.sync(f(a))
  }
  @inline final def syncThrowable[A, B](f: A => B): FnIO[Throwable, A, B] = new FnIO[Throwable, A, B] {
    override def apply[F[+_, +_]: IO2](a: A): F[Throwable, B] = F.syncThrowable(f(a))
  }
  @inline final def fromEither[E, A, B](f: A => Either[E, B]): FnIO[E, A, B] = new FnIO[E, A, B] {
    override def apply[F[+_, +_]: IO2](a: A): F[E, B] = F.fromEither(f(a))
  }
}
