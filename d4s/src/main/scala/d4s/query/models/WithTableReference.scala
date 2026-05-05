package d4s.query.models

import d4s.UnsafeDynamoOps
import d4s.exceptions.{DynamoException, DynamoFailureConditions}
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.predef.SetupTable
import d4s.table.{DynamoTableRef, TablePrefix}
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{ResourceNotFoundException, TableNotFoundException}

import scala.annotation.unused
import scala.concurrent.duration.DurationInt

trait WithTableReference[DQ[+d] <: WithTableReference[DQ, d], +Dec] extends DynamoQuery[DQ, Dec] with WithTableReference.Generic[DQ[Dec]] {
  self: DQ[Dec] =>

  def tableRef: DynamoTableRef[?]
  def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): DQ[Dec]

  /** Use `prefixed` instead to create table dynamically on demand. Import `d4s.unsafe.unsafeDynamoOps` to use anyway. */
  override final def withPrefix[TP: TablePrefix](prefix: TP)(implicit @unused unsafe: UnsafeDynamoOps): DQ[Dec] = withTableReference(_.withPrefix(prefix))

  final def reactiveTableSetup: DQ[Dec] = withInterpreter {
    val interpreter = self.interpreter
    new QueryInterpreter[DQ, Rsp] {
      override def interpret[F[+_, +_]: IO2: Temporal2, Dec0](ctx: QueryCtx[F])(query: DQ[Dec0]): F[DynamoException, Rsp] = {
        QueryInterpreter
          .retryOnFailureAndExecute(120, 1.second)(
            action = interpreter.interpret(ctx)(query)
          )(executeOnFailure = SetupTable(query.tableRef).interpret(ctx).void)(
            condition = DynamoFailureConditions.d4s.tableNotFound
          )
      }
    }
  }

  final def prefixed[TP: TablePrefix](prefix: TP): DQ[Dec] = {
    withTableReference(_.withPrefix(prefix)).reactiveTableSetup
  }

  final def optTableNotExist: DQ[Option[Dec]] = decodeF[Option[Dec]] {
    new QueryDecoder[Rsp, Option[Dec]] {
      override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, Rsp]): F[DynamoException, Option[Dec]] = rsp match {
        case Left(DynamoException(_, _: ResourceNotFoundException)) => F.pure(Option.empty[Dec])
        case Left(DynamoException(_, _: TableNotFoundException))    => F.pure(Option.empty[Dec])
        case other                                                  => decoder.decode[F](other).map(Some(_))
      }
    }
  }
}

object WithTableReference {
  trait Generic[+D] {
    def tableRef: DynamoTableRef[?]
    def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): D

    def withPrefix[TP: TablePrefix](prefix: TP)(implicit @unused unsafe: UnsafeDynamoOps = d4s.unsafe.unsafeDynamoOps): D = withTableReference(_.withPrefix(prefix))
  }
}
