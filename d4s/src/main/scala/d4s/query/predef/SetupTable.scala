package d4s.query.predef

import d4s.exceptions.{DynamoException, DynamoFailureConditions}
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}

final case class SetupTable[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[SetupTable, Option[CreateTableResponse]],
  decoder: QueryDecoder[Option[CreateTableResponse], Dec],
) extends DynamoQuery[SetupTable, Dec]
  with WithTableReference[SetupTable, Dec] {

  override type Rsp = Option[CreateTableResponse]

  override def withInterpreter(f: QueryInterpreter[SetupTable, Option[CreateTableResponse]]): SetupTable[Dec] = copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[Option[CreateTableResponse], C1]): SetupTable[C1] = copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): SetupTable[Dec] = copy(tableRef = f(tableRef))
}

object SetupTable {
  def apply(table: DynamoTableRef[?]): SetupTable[Option[CreateTableResponse]] = {
    new SetupTable(table, interpreter(), QueryDecoder.pure)
  }

  def interpreter(attempts: Int = 120, sleep: FiniteDuration = 1.second): QueryInterpreter[SetupTable, Option[CreateTableResponse]] = {
    new QueryInterpreter[SetupTable, Option[CreateTableResponse]] {
      override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: SetupTable[Dec]): F[DynamoException, Option[CreateTableResponse]] = {
        import query.*
        for {
          maybeResponse <- CreateTable(query.tableRef)
            .retryOnFailure(attempts, sleep)(DynamoFailureConditions.d4s.operationLimitReached)
            .interpret(ctx).redeem(
              {
                case DynamoException(_, _: ResourceInUseException) => F.pure(None)
                case e                                             => F.fail(e)
              },
              rsp => F.pure(Some(rsp)),
            )
          _ <- F.traverse(maybeResponse) {
            rsp =>
              for {
                _ <- F.when(tableRef.ttlField.nonEmpty) {
                  UpdateTTL(tableRef)
                    .retryOnFailure(attempts, sleep)(DynamoFailureConditions.d4s.tableNotFound)
                    .interpret(ctx).void
                }
                _ <- F.when(tableRef.tags.nonEmpty) {
                  UpdateTableTags(tableRef, rsp.tableDescription().tableArn())
                    .retryOnFailure(attempts, sleep)(DynamoFailureConditions.d4s.tableNotFound)
                    .interpret(ctx).void
                }
                _ <- F.when(tableRef.backupEnabled) {
                  UpdateContinuousBackups(tableRef, backupEnabled = true)
                    .retryOnFailure(attempts, sleep)(DynamoFailureConditions.d4s.tableNotFound.orElse(DynamoFailureConditions.d4s.backupUnavailable))
                    .interpret(ctx).void
                }
                _ <- ctx.logger.info(s"Created ${tableRef.tableName -> "tableName"}; ${tableRef.provisioning -> "provisioning"}.")
              } yield ()
          }
        } yield maybeResponse
      }
    }
  }
}
