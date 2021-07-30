package net.playq.tk.control

import izumi.functional.bio.{F, IO2, Primitives2}

trait Mutex2[F[_, _]] {
  def bracket[E, A](f: F[E, A]): F[E, A]
}

object Mutex2 {
  def make[F[+_, +_]: IO2: Primitives2]: F[Nothing, Mutex2[F]] = {
    F.mkSemaphore(1).map {
      semaphore =>
        new Mutex2[F] {
          override def bracket[E, A](f: F[E, A]): F[E, A] = {
            F.bracket(semaphore.acquire)(_ => semaphore.release)(_ => f)
          }
        }
    }
  }
}
