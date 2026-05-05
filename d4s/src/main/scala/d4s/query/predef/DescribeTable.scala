package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

final case class DescribeTable[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[DescribeTable, DescribeTableResponse],
  decoder: QueryDecoder[DescribeTableResponse, Dec],
) extends DynamoQuery.WithRequest[DescribeTable, Dec]
  with WithTableReference[DescribeTable, Dec] {

  override type Rq  = DescribeTableRequest
  override type Rsp = DescribeTableResponse

  override def withInterpreter(f: QueryInterpreter[DescribeTable, DescribeTableResponse]): DescribeTable[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[DescribeTableResponse, C1]): DescribeTable[C1] =
    copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): DescribeTable[Dec] =
    copy(tableRef = f(tableRef))

  override def awsRequest: DescribeTableRequest = {
    DescribeTableRequest
      .builder()
      .tableName(tableRef.tableName)
      .build()
  }
}

object DescribeTable {
  def apply(table: DynamoTableRef[?]): DescribeTable[DescribeTableResponse] = {
    new DescribeTable(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[DescribeTable, DescribeTableResponse] = new QueryInterpreter[DescribeTable, DescribeTableResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DescribeTable[Dec]): F[DynamoException, DescribeTableResponse] = {
      ctx.execute("DescribeTable", query.tableRef.tableName)(_.executeFuture(_.describeTable(query.awsRequest)))
    }
  }
}
