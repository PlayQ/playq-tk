package d4s.query

import d4s.exceptions.DynamoException
import d4s.query.effect.*
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.DynamoDbRequest

import scala.concurrent.duration.FiniteDuration

trait DynamoQuery[DQ[+_], +Dec] extends DynamoRequest[Dec] {
  self: DQ[Dec] =>

  override type SelfType[+d] = DQ[d]

  def interpreter: QueryInterpreter[DQ, Rsp]
  def withInterpreter(f: QueryInterpreter[DQ, Rsp]): DQ[Dec]

  @inline final def execute[F[+_, +_]: IO2: Temporal2](ctx: QueryCtx[F]): F[DynamoException, Dec] = {
    interpreter.interpret[F, Dec](ctx)(this).attempt.flatMap(decoder.decode[F](_))
  }

  @inline final def execute[F[+_, +_]: IO2: Temporal2](ctx: QueryCtx[F], logger: QueryLogger): F[DynamoException, Dec] = {
    logger.withLogger(ctx.logger)(execute(ctx))
  }

  @inline final def interpret[F[+_, +_]: IO2: Temporal2](ctx: QueryCtx[F]): F[DynamoException, Rsp] = {
    interpreter.interpret[F, Dec](ctx)(this)
  }

  @inline final def retryOnFailure[D >: Dec](
    attempts: Int,
    sleep: FiniteDuration,
  )(condition: PartialFunction[DynamoException, Boolean]
  ): DQ[D] = {
    withInterpreter(interpreter.retryOnFailure(attempts, sleep)(condition))
  }

  @inline final def retryOnFailureAndExecute[A, D >: Dec](
    attempts: Int,
    sleep: FiniteDuration,
  )(executeOnFailure: => FnIO[DynamoException, Unit, A]
  )(condition: PartialFunction[DynamoException, Boolean]
  ): DQ[D] = {
    withInterpreter(interpreter.retryOnFailureAndExecute(attempts, sleep)(executeOnFailure)(condition))
  }

  @inline final def void: DQ[Unit]           = map(_ => ())
  @inline final def as[C1](f: => C1): DQ[C1] = map(_ => f)
}

object DynamoQuery {
  abstract class WithRequest[DQ[+_], +Dec] extends DynamoQuery[DQ, Dec] {
    self: DQ[Dec] =>
    type Rq <: DynamoDbRequest
    def awsRequest: Rq
  }

  final class Raw[Rsp0, +Dec](
    override val decoder: QueryDecoder[Rsp0, Dec],
    override val interpreter: QueryInterpreter[DynamoQuery.Raw[Rsp0, +_], Rsp0],
  ) extends DynamoQuery[DynamoQuery.Raw[Rsp0, +_], Dec] {
    self =>

    override type Rsp = Rsp0

    override def withInterpreter(f: QueryInterpreter[DynamoQuery.Raw[Rsp, +_], Rsp]): DynamoQuery.Raw[Rsp, Dec] = {
      new DynamoQuery.Raw[Rsp, Dec](self.decoder, f)
    }

    override def decodeF[C1](f: QueryDecoder[Rsp, C1]): DynamoQuery.Raw[Rsp, C1] = {
      new DynamoQuery.Raw[Rsp, C1](f, self.interpreter)
    }
  }

  object Raw {
    @inline def apply[Rsp0, Dec](decoder: QueryDecoder[Rsp0, Dec], interpreter: QueryInterpreter[DynamoQuery.Raw[Rsp0, +_], Rsp0]): Raw[Rsp0, Dec] = {
      new Raw[Rsp0, Dec](decoder, interpreter)
    }
  }
}
