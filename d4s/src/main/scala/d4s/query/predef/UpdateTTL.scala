package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{TimeToLiveSpecification, UpdateTimeToLiveRequest, UpdateTimeToLiveResponse}

final case class UpdateTTL[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[UpdateTTL, UpdateTimeToLiveResponse],
  decoder: QueryDecoder[UpdateTimeToLiveResponse, Dec],
) extends DynamoQuery.WithRequest[UpdateTTL, Dec]
  with WithTableReference[UpdateTTL, Dec] {

  override type Rq  = UpdateTimeToLiveRequest
  override type Rsp = UpdateTimeToLiveResponse

  override def withInterpreter(f: QueryInterpreter[UpdateTTL, UpdateTimeToLiveResponse]): UpdateTTL[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[UpdateTimeToLiveResponse, C1]): UpdateTTL[C1] =
    copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): UpdateTTL[Dec] =
    copy(tableRef = f(tableRef))

  override def awsRequest: UpdateTimeToLiveRequest = {
    tableRef.ttlField.fold(throw new RuntimeException("Table reference does not contain ttlField. Must be a bug.")) {
      ttlField =>
        val ttlSpec = TimeToLiveSpecification.builder().enabled(true).attributeName(ttlField).build()
        UpdateTimeToLiveRequest.builder().tableName(tableRef.tableName).timeToLiveSpecification(ttlSpec).build()
    }
  }
}

object UpdateTTL {
  def apply(table: DynamoTableRef[?]): UpdateTTL[UpdateTimeToLiveResponse] = {
    new UpdateTTL(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[UpdateTTL, UpdateTimeToLiveResponse] = new QueryInterpreter[UpdateTTL, UpdateTimeToLiveResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: UpdateTTL[Dec]): F[DynamoException, UpdateTimeToLiveResponse] = {
      val tableName = query.tableRef.tableName
      val ttlField  = query.tableRef.ttlField
      ctx.execute("UpdateTimeToLive", tableName)(_.executeFuture(_.updateTimeToLive(query.awsRequest))) <*
      ctx.logger.info(s"TTL for $tableName was updated: $ttlField.")
    }
  }
}
