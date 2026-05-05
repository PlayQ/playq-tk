package d4s.query.predef

import d4s.config.ProvisionedThroughputConfig
import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import d4s.table.index.{GlobalIndexUpdate, ProvisionedGlobalIndex}
import izumi.functional.bio.{IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.*

import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

final case class UpdateTable[+Dec](
  tableRef: DynamoTableRef[?],
  interpreter: QueryInterpreter[UpdateTable, UpdateTableResponse],
  decoder: QueryDecoder[UpdateTableResponse, Dec],
  newProvisioning: Option[ProvisionedThroughputConfig] = None,
  newGlobalIndex: Option[ProvisionedGlobalIndex[?, ?]] = None,
  updateGlobalIndexes: Set[GlobalIndexUpdate]          = Set.empty,
  deleteGlobalIndex: Option[String]                    = None,
) extends DynamoQuery.WithRequest[UpdateTable, Dec]
  with WithTableReference[UpdateTable, Dec] {

  override type Rq  = UpdateTableRequest
  override type Rsp = UpdateTableResponse

  override def withInterpreter(f: QueryInterpreter[UpdateTable, UpdateTableResponse]): UpdateTable[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[UpdateTableResponse, C1]): UpdateTable[C1] =
    copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): UpdateTable[Dec] =
    copy(tableRef = f(tableRef))

  def withNewProvisioning(provisioning: ProvisionedThroughputConfig): UpdateTable[Dec] =
    copy(newProvisioning = Some(provisioning))

  def withIndexToCreate(index: ProvisionedGlobalIndex[?, ?]): UpdateTable[Dec] =
    copy(newGlobalIndex = Some(index))

  def withIndexesToUpdate(indexes: Set[GlobalIndexUpdate]): UpdateTable[Dec] =
    copy(updateGlobalIndexes = indexes)

  def withIndexToDelete(index: String): UpdateTable[Dec] =
    copy(deleteGlobalIndex = Some(index))

  override def awsRequest: UpdateTableRequest = {
    val allAttributes: Seq[AttributeDefinition] = {
      Seq(
        tableRef.key.toAttributes,
        newGlobalIndex.map(_.key.toAttributes).getOrElse(List.empty),
      ).flatten.distinct
    }

    val newIndexes = newGlobalIndex.map(_.asCreateAction).map {
      GlobalSecondaryIndexUpdate.builder().create(_).build()
    }

    val indexToUpdate = updateGlobalIndexes.map(_.asUpdateAction).map {
      GlobalSecondaryIndexUpdate.builder().update(_).build()
    }

    val indexToRemove = deleteGlobalIndex
      .map(DeleteGlobalSecondaryIndexAction.builder().indexName(_).build())
      .map(GlobalSecondaryIndexUpdate.builder().delete(_).build())

    val allUpdates = newIndexes ++ indexToUpdate ++ indexToRemove

    UpdateTableRequest
      .builder()
      .tableName(tableRef.tableName)
      .attributeDefinitions(allAttributes.asJavaCollection)
      .pipe(b => if (allUpdates.isEmpty) b else b.globalSecondaryIndexUpdates(allUpdates.toList.asJavaCollection))
      .pipe(builder => newProvisioning.fold(builder)(_.configure(builder)))
      .build()
  }
}

object UpdateTable {
  def apply(table: DynamoTableRef[?]): UpdateTable[UpdateTableResponse] = {
    new UpdateTable(table, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[UpdateTable, UpdateTableResponse] = new QueryInterpreter[UpdateTable, UpdateTableResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: UpdateTable[Dec]): F[DynamoException, UpdateTableResponse] = {
      val tableName = query.tableRef.tableName
      val request   = query.awsRequest
      ctx.execute("UpdateTable", tableName)(_.executeFuture(_.updateTable(request))) <*
      ctx.logger.info(s"Table $tableName were updated, $request")
    }
  }
}
