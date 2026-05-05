package d4s.query.effect

import d4s.config.DynamoBatchConfig
import d4s.exceptions.DynamoException
import izumi.functional.bio.{F, IO2, Temporal2}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait QueryInterpreter[-DQ[_], +Rsp] {
  self =>

  def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DQ[Dec]): F[DynamoException, Rsp]

  @inline final def void: QueryInterpreter[DQ, Unit] = map(_ => ())

  @inline final def map[C](f: Rsp => C): QueryInterpreter[DQ, C] = flatMap(FnIO.sync(f))

  @inline final def flatMap[C](f: FnIO[DynamoException, Rsp, C]): QueryInterpreter[DQ, C] = {
    new QueryInterpreter[DQ, C] {
      override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DQ[Dec]): F[DynamoException, C] = {
        self.interpret[F, Dec](ctx)(query).flatMap(b => f.apply[F](b))
      }
    }
  }

  @inline final def redeem[C](onFailure: FnIO[DynamoException, DynamoException, C], onSuccess: FnIO[DynamoException, Rsp, C]): QueryInterpreter[DQ, C] = {
    new QueryInterpreter[DQ, C] {
      override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DQ[Dec]): F[DynamoException, C] = {
        self.interpret(ctx)(query).redeem(onFailure.apply[F], onSuccess.apply[F])
      }
    }
  }

  @inline final def retryOnFailure(
    attempts: Int,
    sleep: FiniteDuration,
  )(condition: PartialFunction[DynamoException, Boolean]
  ): QueryInterpreter[DQ, Rsp] = new QueryInterpreter[DQ, Rsp] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DQ[Dec]): F[DynamoException, Rsp] = {
      QueryInterpreter.retryOnFailure(attempts, sleep)(self.interpret(ctx)(query))(condition)
    }
  }

  @inline final def retryOnFailureAndExecute[A](
    attempts: Int,
    sleep: FiniteDuration,
  )(executeOnFailure: => FnIO[DynamoException, Unit, A]
  )(condition: PartialFunction[DynamoException, Boolean]
  ): QueryInterpreter[DQ, Rsp] = new QueryInterpreter[DQ, Rsp] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DQ[Dec]): F[DynamoException, Rsp] = {
      QueryInterpreter.retryOnFailureAndExecute(attempts, sleep)(self.interpret(ctx)(query))(executeOnFailure.apply[F](()).void)(condition)
    }
  }
}

object QueryInterpreter {
  @inline def batchLoop[F[+_, +_]: IO2: Temporal2, Rq, Rsp, K, V](
    rq0: Rq,
    batchConfig: DynamoBatchConfig,
  )(interpret: Rq => F[Throwable, Rsp]
  )(getUnprocessedKeys: Rsp => java.util.Map[K, V]
  )(mkNewRq: (Rq, java.util.Map[K, V]) => Rq
  ): F[Throwable, Rsp] = {
    val sleepDuration = batchConfig.unprocessedBatchSleep getOrElse 1.second

    def go(rq: Rq): F[Throwable, Rsp] = interpret(rq).flatMap {
      rsp =>
        val unprocessed = getUnprocessedKeys(rsp)
        F.ifThenElse(unprocessed.isEmpty)(
          F.pure(rsp),
          F.sleep(sleepDuration) *> go(mkNewRq(rq, unprocessed)),
        )
    }

    go(rq0)
  }

  @inline def retryOnFailure[F[+_, +_]: IO2: Temporal2, E, A](action: F[E, A])(condition: PartialFunction[E, Boolean]): F[E, A] = {
    retryOnFailure(120, 1.second)(action)(condition)
  }

  @inline def retryOnFailure[F[+_, +_]: IO2: Temporal2, E, A](
    attempts: Int,
    sleep: FiniteDuration,
  )(action: => F[E, A]
  )(condition: PartialFunction[E, Boolean]
  ): F[E, A] = {
    action.catchSome {
      case e if condition.lift(e).getOrElse(false) && attempts > 0 =>
        F.sleep(sleep) *> retryOnFailure(attempts - 1, sleep)(action)(condition)
    }
  }

  @inline def retryOnFailureAndExecute[F[+_, +_]: IO2: Temporal2, E, A](
    action: => F[E, A]
  )(executeOnFailure: => F[E, Unit]
  )(condition: PartialFunction[E, Boolean]
  ): F[E, A] = {
    retryOnFailureAndExecute(120, 1.second)(action)(executeOnFailure)(condition)
  }

  @inline def retryOnFailureAndExecute[F[+_, +_]: IO2: Temporal2, E, A](
    attempts: Int,
    sleep: FiniteDuration,
  )(action: => F[E, A]
  )(executeOnFailure: => F[E, Unit]
  )(condition: PartialFunction[E, Boolean]
  ): F[E, A] = {
    action.catchSome {
      case e if condition.lift(e).getOrElse(false) && attempts > 0 =>
        executeOnFailure *> F.sleep(sleep) *> retryOnFailure(attempts - 1, sleep)(action)(condition)
    }
  }
}
