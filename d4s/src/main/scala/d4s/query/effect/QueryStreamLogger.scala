package d4s.query.effect

import izumi.functional.bio.IO2

trait QueryStreamLogger[F[+_, +_]] {
  def wrap[E <: Throwable, A](ctx: QueryCtx[F])(f: F[E, A]): F[E, A]
}

object QueryStreamLogger {
  @inline def empty[F[+_, +_]]: QueryStreamLogger[F] = new QueryStreamLogger[F] {
    override def wrap[E <: Throwable, A](ctx: QueryCtx[F])(f: F[E, A]): F[E, A] = f
  }
  @inline def default[F[+_, +_]: IO2]: QueryStreamLogger[F] = new QueryStreamLogger[F] {
    override def wrap[E <: Throwable, A](ctx: QueryCtx[F])(f: F[E, A]): F[E, A] = {
      QueryLogger.default.withLogger(ctx.logger)(f)
    }
  }
}
