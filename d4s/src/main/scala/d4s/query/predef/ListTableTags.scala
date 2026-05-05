package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

final case class ListTableTags[+Dec](
  tableRef: DynamoTableRef[?],
  dynamoResourceName: String,
  interpreter: QueryInterpreter[ListTableTags, ListTagsOfResourceResponse],
  decoder: QueryDecoder[ListTagsOfResourceResponse, Dec],
) extends DynamoQuery.WithRequest[ListTableTags, Dec]
  with WithTableReference[ListTableTags, Dec] {

  override type Rq  = ListTagsOfResourceRequest
  override type Rsp = ListTagsOfResourceResponse

  override def withInterpreter(f: QueryInterpreter[ListTableTags, ListTagsOfResourceResponse]): ListTableTags[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[ListTagsOfResourceResponse, C1]): ListTableTags[C1] =
    copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): ListTableTags[Dec] =
    copy(tableRef = f(tableRef))

  override def awsRequest: ListTagsOfResourceRequest = {
    ListTagsOfResourceRequest
      .builder()
      .resourceArn(dynamoResourceName)
      .build()
  }
}
object ListTableTags {
  def apply(table: DynamoTableRef[?], dynamoResourceName: String): ListTableTags[ListTagsOfResourceResponse] = {
    new ListTableTags(table, dynamoResourceName, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[ListTableTags, ListTagsOfResourceResponse] = new QueryInterpreter[ListTableTags, ListTagsOfResourceResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: ListTableTags[Dec]): F[DynamoException, ListTagsOfResourceResponse] = {
      ctx.execute("ListTagsOfResource", query.tableRef.tableName)(_.executeFuture(_.listTagsOfResource(query.awsRequest)))
    }
  }
}
