package d4s.query.predef

import d4s.UnsafeDynamoOps
import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.{WithPages, WithPagesSize}
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{ListTablesRequest, ListTablesResponse}

import scala.util.chaining.*

/**
  * ListTables Request builder.
  * Inherits WithPages, so that it can not be executed directly with DynamoConnector.
  * To execute this query you should complete it using one of the following methods (or it's flattenized variants):
  * - [[WithPages.PagedReader.readPage]]
  * - [[WithPages.PagedReader.readAll]]
  * - [[WithPages.PagedReader.readLimit]]
  */
final case class ListTables[+Dec](
  interpreter: QueryInterpreter[ListTables, ListTablesResponse],
  decoder: QueryDecoder[ListTablesResponse, Dec],
  startTable: Option[String] = None,
  pageSize: Option[Int]      = None,
) extends DynamoQuery.WithRequest[ListTables, Dec]
  with WithPagesSize[ListTables[Dec]]
  with WithPages[ListTables, String, Dec] {

  override type Rq  = ListTablesRequest
  override type Rsp = ListTablesResponse

  override def withInterpreter(f: QueryInterpreter[ListTables, ListTablesResponse]): ListTables[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[ListTablesResponse, C1]): ListTables[C1] =
    copy(decoder = f)

  override def withPageKey(pageKey: Option[String]): ListTables[Dec] =
    copy(startTable = pageKey)

  override def withPageSize(l: Int)(implicit unsafe: UnsafeDynamoOps): ListTables[Dec] =
    copy(pageSize = Some(l))

  override def extractPageKey(rsp: ListTablesResponse): Option[String] =
    Option(rsp.lastEvaluatedTableName())

  override def extractItemsCount(rsp: ListTablesResponse): Int =
    rsp.tableNames().size()

  override def awsRequest: ListTablesRequest = {
    ListTablesRequest
      .builder()
      .pipe(b => startTable.fold(b)(b.exclusiveStartTableName))
      .build()
  }
}

object ListTables {
  def apply(): ListTables[ListTablesResponse] = {
    new ListTables(interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[ListTables, ListTablesResponse] = new QueryInterpreter[ListTables, ListTablesResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: ListTables[Dec]): F[DynamoException, ListTablesResponse] = {
      ctx.execute("ListTables")(_.executeFuture(_.listTables(query.awsRequest)))
    }
  }
}
