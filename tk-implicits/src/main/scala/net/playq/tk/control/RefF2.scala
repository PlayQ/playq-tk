package net.playq.tk.control

import izumi.functional.bio.{F, IO2, Primitives2}

trait RefF2[F[_, _], A] {
  def get: F[Nothing, A]
  def set(a: A): F[Nothing, Unit]

  def modify[E, B](f: A => F[E, (B, A)]): F[E, B]
  def updateSync[E](f: A => A): F[Nothing, A]
  def update[E](f: A => F[E, A]): F[E, A]
  def update_[E](f: A => F[E, A]): F[E, Unit]
}

object RefF2 {
  def make[F[+_, +_]: IO2: Primitives2, A](a: A): F[Nothing, RefF2[F, A]] = {
    for {
      mutex <- Mutex2.make[F]
      ref   <- F.mkRef(a)
    } yield {
      new RefF2[F, A] {
        override def get: F[Nothing, A] = ref.get

        override def set(a: A): F[Nothing, Unit] = mutex.bracket(ref.set(a))

        override def modify[E, B](f: A => F[E, (B, A)]): F[E, B] = mutex.bracket {
          for {
            a0    <- ref.get
            res   <- f(a0)
            (b, a) = res
            _     <- ref.set(a)
          } yield b
        }

        override def updateSync[E](f: A => A): F[Nothing, A] = {
          update(f.andThen(F.pure(_)))
        }

        override def update[E](f: A => F[E, A]): F[E, A] = mutex.bracket {
          for {
            a0 <- ref.get
            a  <- f(a0)
            _  <- ref.set(a)
          } yield a
        }

        override def update_[E](f: A => F[E, A]): F[E, Unit] = update(f).void
      }
    }
  }
}
