package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.IterableHasAsJava

final case class UpdateTableTags[+Dec](
  tableRef: DynamoTableRef[?],
  dynamoResourceName: String,
  interpreter: QueryInterpreter[UpdateTableTags, TagResourceResponse],
  decoder: QueryDecoder[TagResourceResponse, Dec],
  tagsToAdd: Map[String, String] = Map.empty,
) extends DynamoQuery.WithRequest[UpdateTableTags, Dec]
  with WithTableReference[UpdateTableTags, Dec] {

  override type Rq  = TagResourceRequest
  override type Rsp = TagResourceResponse

  override def withInterpreter(f: QueryInterpreter[UpdateTableTags, TagResourceResponse]): UpdateTableTags[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[TagResourceResponse, C1]): UpdateTableTags[C1] =
    copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): UpdateTableTags[Dec] =
    copy(tableRef = f(tableRef))

  def withTags(t: Map[String, String]): UpdateTableTags[Dec] =
    copy(tagsToAdd = tagsToAdd ++ t)

  def requestedTags: Map[String, String] = tableRef.tags ++ tagsToAdd

  override def awsRequest: TagResourceRequest = {
    val tags = requestedTags.map { case (k, v) => Tag.builder().key(k).value(v).build() }.asJavaCollection
    TagResourceRequest
      .builder()
      .resourceArn(dynamoResourceName)
      .tags(tags)
      .build()
  }
}

object UpdateTableTags {
  def apply(table: DynamoTableRef[?], dynamoResourceName: String): UpdateTableTags[TagResourceResponse] = {
    new UpdateTableTags(table, dynamoResourceName, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[UpdateTableTags, TagResourceResponse] = new QueryInterpreter[UpdateTableTags, TagResourceResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: UpdateTableTags[Dec]): F[DynamoException, TagResourceResponse] = {
      val tags               = query.requestedTags.map { case (k, v) => s"$k -> $v" }.mkString("[", ", ", "]")
      val tableName          = query.tableRef.tableName
      val dynamoResourceName = query.dynamoResourceName
      ctx.dynamoConfig.maybeLocalUrl match {
        case Some(localEnv) =>
          ctx.logger.info(s"Skipping tags update for $tableName in mock environment: $localEnv, $tags") *>
          F.pure(TagResourceResponse.builder().build())
        case None =>
          ctx.execute("UpdateTableTags", tableName)(_.executeFuture(_.tagResource(query.awsRequest))) <*
          ctx.logger.info(s"Tags for $tableName were updated: $dynamoResourceName, $tags.")
      }
    }
  }
}
