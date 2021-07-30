package net.playq.tk.implicits

import izumi.functional.bio.Applicative2
import izumi.fundamentals.platform.functional.Identity2
import izumi.fundamentals.platform.language.Quirks._

object Identity2Instances {
  implicit val bioapplicativeIdentity2: Applicative2[Identity2] = new Applicative2[Identity2] {
    override def pure[A](a: A): Identity2[Nothing, A] = a
    override def map2[R, E, A, B, C](firstOp: Identity2[E, A], secondOp: => Identity2[E, B])(f: (A, B) => C): Identity2[E, C] = {
      f(firstOp, secondOp)
    }
    override def *>[R, E, A, B](firstOp: Identity2[E, A], secondOp: => Identity2[E, B]): Identity2[E, B] = {
      firstOp.discard(); secondOp
    }
    override def <*[R, E, A, B](firstOp: Identity2[E, A], secondOp: => Identity2[E, B]): Identity2[E, A] = {
      secondOp; firstOp
    }
    override def traverse[R, E, A, B](l: Iterable[A])(f: A => Identity2[E, B]): Identity2[E, List[B]] = l.map(f).toList
    override def map[R, E, A, B](r: Identity2[E, A])(f: A => B): Identity2[E, B]                      = f(r)
  }
}
