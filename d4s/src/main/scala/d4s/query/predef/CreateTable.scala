package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

import java.util
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

final case class CreateTable[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[CreateTable, CreateTableResponse],
  decoder: QueryDecoder[CreateTableResponse, Dec],
) extends DynamoQuery.WithRequest[CreateTable, Dec]
  with WithTableReference[CreateTable, Dec] {

  override type Rq  = CreateTableRequest
  override type Rsp = CreateTableResponse

  override def withInterpreter(f: QueryInterpreter[CreateTable, CreateTableResponse]): CreateTable[Dec] = copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[CreateTableResponse, C1]): CreateTable[C1] = copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): CreateTable[Dec] = copy(tableRef = f(tableRef))

  override def awsRequest: CreateTableRequest = {
    val allAttributes: Seq[AttributeDefinition] = {
      Seq(
        tableRef.key.toAttributes,
        tableRef.globalIndexes.flatMap(_.key.toAttributes),
        tableRef.localIndexes.flatMap(_.key.toAttributes),
        tableRef.additionalAttributes.map(_.toAttribute),
      ).flatten.distinct
    }

    val globalIndexesRaw: util.Collection[GlobalSecondaryIndex] = {
      tableRef.globalIndexes.map(i => i.toAwsIndex(tableRef.provisioning.getIndexProvisioning(i.name))).asJavaCollection
    }

    val localIndexesRaw: util.Collection[LocalSecondaryIndex] = {
      tableRef.localIndexes.map(_.toAwsIndex).asJavaCollection
    }

    CreateTableRequest
      .builder()
      .tableName(tableRef.tableName)
      .keySchema(tableRef.key.toJava)
      .pipe(if (globalIndexesRaw.isEmpty) identity else _.globalSecondaryIndexes(globalIndexesRaw))
      .pipe(if (localIndexesRaw.isEmpty) identity else _.localSecondaryIndexes(localIndexesRaw))
      .attributeDefinitions(allAttributes.asJavaCollection)
      .pipe(tableRef.provisioning.tableProvisioning.configure(_))
      .build()
  }
}
object CreateTable {
  def apply(table: DynamoTableRef[?]): CreateTable[CreateTableResponse] = {
    new CreateTable(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[CreateTable, CreateTableResponse] = {
    new QueryInterpreter[CreateTable, CreateTableResponse] {
      override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: CreateTable[Dec]): F[DynamoException, CreateTableResponse] = {
        ctx.execute("CreateTable", query.tableRef.tableName)(_.executeFuture(_.createTable(query.awsRequest)))
      }
    }
  }
}
