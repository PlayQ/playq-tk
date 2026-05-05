package net.playq.tk.test

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, F, Fork2, Primitives2, Promise2, Temporal2}
import net.playq.tk.test.config.TestDeadlineConfig

final class TestDeadline[F[+_, +_]: Async2: Fork2](
  deadlinePromise: Promise2[F, Throwable, Nothing]
) {
  def apply[A](f: F[Throwable, A]): F[Throwable, A] = {
    f.fork.flatMap {
      fiber =>
        F.race(fiber.join, deadlinePromise.await).guarantee(fiber.interrupt)
    }
  }
}

object TestDeadline {
  final class Resource[F[+_, +_]: Async2: Temporal2: Primitives2: Fork2](
    testDeadlineConfig: TestDeadlineConfig
  ) extends Lifecycle.Of[F[Throwable, _], TestDeadline[F]](
      for {
        deadlinePromise <- Lifecycle.liftF(F.mkPromise[Throwable, Nothing])
        totalBudget      = testDeadlineConfig.totalBudget
        _               <- Lifecycle.fork(F.sleep(totalBudget) *> deadlinePromise.fail(new TestDeadlineException(s"Test budget of $totalBudget exhausted")))
      } yield new TestDeadline[F](deadlinePromise)
    )

  final class TestDeadlineException(message: String) extends RuntimeException(message)
}
