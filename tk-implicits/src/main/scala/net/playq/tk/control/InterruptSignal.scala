package net.playq.tk.control

import izumi.functional.bio.{Applicative2, Error2, F, Primitives2}

trait InterruptSignal[F[_, _]] {
  def isInterrupted: F[Nothing, Boolean]
  def awaitInterrupt: F[Nothing, Either[Throwable, Unit]]
  def interrupt: F[Nothing, Boolean]
}

object InterruptSignal {
  def empty[F[+_, +_]: Applicative2]: InterruptSignal[F] = new InterruptSignal[F] {
    override def isInterrupted: F[Nothing, Boolean]                  = F.pure(false)
    override def awaitInterrupt: F[Nothing, Either[Throwable, Unit]] = F.pure(Left(new RuntimeException("Signal is discarded.")))
    override def interrupt: F[Nothing, Boolean]                      = F.pure(false)
  }

  def make[F[+_, +_]: Primitives2: Error2]: F[Nothing, InterruptSignal[F]] = {
    F.mkPromise[Throwable, Unit].map {
      promise =>
        new InterruptSignal[F] {
          def isInterrupted: F[Nothing, Boolean]                  = promise.poll.map(_.nonEmpty)
          def awaitInterrupt: F[Nothing, Either[Throwable, Unit]] = promise.await.attempt
          def interrupt: F[Nothing, Boolean]                      = promise.succeed(())
        }
    }
  }
}
