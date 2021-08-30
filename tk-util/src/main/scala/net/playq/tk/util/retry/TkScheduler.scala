package net.playq.tk.util.retry

import izumi.functional.bio.{Clock2, F, IO2, Temporal2}
import net.playq.tk.util.retry.RetryPolicy.{ControllerDecision, RetryFunction}

import scala.jdk.DurationConverters.*

trait TkScheduler[F[+_, +_]] {
  def repeat[E, A, B](eff: F[E, A])(policy: RetryPolicy[F, A, B]): F[E, A]
  def retryOrElse[E, E1 >: E, E2, A, A2 >: A, S](eff: F[E, A])(policy: RetryPolicy[F, E1, S])(orElse: E1 => F[E2, A2]): F[E2, A2]
}

object TkScheduler {
  def apply[F[+_, +_]: TkScheduler]: TkScheduler[F] = implicitly

  final class Impl[F[+_, +_]: IO2: Temporal2: Clock2]() extends TkScheduler[F] {
    override def repeat[E, A, B](eff: F[E, A])(policy: RetryPolicy[F, A, B]): F[E, A] = {
      def loop(in: A, makeDecision: RetryFunction[F, A, B]): F[E, A] = {
        (for {
          now <- Clock2[F].now()
          dec <- makeDecision(now, in)
          res = dec match {
            case ControllerDecision.Repeat(_, interval, action) =>
              F.sleep(java.time.Duration.between(now, interval).toScala) *> eff.flatMap(loop(_, action))
            case ControllerDecision.Stop(_) =>
              F.pure(in)
          }
        } yield res).flatten
      }

      eff.flatMap(out => loop(out, policy.action))
    }

    override def retryOrElse[E, E1 >: E, E2, A, A2 >: A, S](eff: F[E, A])(policy: RetryPolicy[F, E1, S])(orElse: E1 => F[E2, A2]): F[E2, A2] = {
      def loop(in: E1, makeDecision: RetryFunction[F, E1, S]): F[E2, A2] = {
        (for {
          now <- Clock2[F].now()
          dec <- makeDecision(now, in)
          res = dec match {
            case ControllerDecision.Repeat(_, interval, action) =>
              F.sleep(java.time.Duration.between(now, interval).toScala) *> eff.catchAll(loop(_, action))
            case ControllerDecision.Stop(_) =>
              orElse(in)
          }
        } yield res).flatten
      }

      eff.catchAll(err => loop(err, policy.action))
    }
  }
}
