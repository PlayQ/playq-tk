package d4s.query.predef

import d4s.exceptions.{DynamoException, DynamoFailureConditions}
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{DeleteTableRequest, DeleteTableResponse, ResourceNotFoundException, TableNotFoundException}

import scala.concurrent.duration.DurationInt

final case class DeleteTable[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[DeleteTable, DeleteTableResponse],
  decoder: QueryDecoder[DeleteTableResponse, Dec],
) extends DynamoQuery.WithRequest[DeleteTable, Dec]
  with WithTableReference[DeleteTable, Dec] {

  override type Rq  = DeleteTableRequest
  override type Rsp = DeleteTableResponse

  override def withInterpreter(f: QueryInterpreter[DeleteTable, DeleteTableResponse]): DeleteTable[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[DeleteTableResponse, C1]): DeleteTable[C1] =
    copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): DeleteTable[Dec] =
    copy(tableRef = f(tableRef))

  override def awsRequest: DeleteTableRequest = {
    DeleteTableRequest
      .builder()
      .tableName(tableRef.tableName)
      .build()
  }
}

object DeleteTable {
  def apply(table: DynamoTableRef[?]): DeleteTable[DeleteTableResponse] = {
    new DeleteTable(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[DeleteTable, DeleteTableResponse] = new QueryInterpreter[DeleteTable, DeleteTableResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DeleteTable[Dec]): F[DynamoException, DeleteTableResponse] = {
      val tableName = query.tableRef.tableName
      ctx.execute("DeleteTable", tableName) {
        client =>
          QueryInterpreter
            .retryOnFailure(120, 1.second)(client.executeFuture(_.deleteTable(query.awsRequest)))(
              DynamoFailureConditions.throwable.resourceInUseOrLimitReached
            ).catchSome {
              case _: ResourceNotFoundException | _: TableNotFoundException => F.pure(DeleteTableResponse.builder().build())
            }
      } <* ctx.logger.info(s"Deleted ${tableName -> "tableName"}.")
    }
  }
}
