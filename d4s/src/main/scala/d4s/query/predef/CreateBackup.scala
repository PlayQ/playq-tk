package d4s.query.predef

import d4s.exceptions.{DynamoException, DynamoFailureConditions}
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithTableReference
import d4s.table.DynamoTableRef
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{CreateBackupRequest, CreateBackupResponse}

import scala.concurrent.duration.DurationInt

final case class CreateBackup[+Dec](
  tableRef: DynamoTableRef[?],
  backupName: String,
  interpreter: QueryInterpreter[CreateBackup, CreateBackupResponse],
  decoder: QueryDecoder[CreateBackupResponse, Dec],
) extends DynamoQuery.WithRequest[CreateBackup, Dec]
  with WithTableReference[CreateBackup, Dec] {

  override type Rq  = CreateBackupRequest
  override type Rsp = CreateBackupResponse

  override def withInterpreter(f: QueryInterpreter[CreateBackup, CreateBackupResponse]): CreateBackup[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[CreateBackupResponse, C1]): CreateBackup[C1] =
    copy(decoder = f)

  override def withTableReference(f: DynamoTableRef[?] => DynamoTableRef[?]): CreateBackup[Dec] =
    copy(tableRef = f(tableRef))

  def withBackupName(backupName: String): CreateBackup[Dec] =
    copy(backupName = backupName)

  override def awsRequest: CreateBackupRequest = {
    CreateBackupRequest
      .builder()
      .tableName(tableRef.tableName)
      .backupName(backupName)
      .build()
  }
}

object CreateBackup {
  def apply(table: DynamoTableRef[?], backupName: String): CreateBackup[CreateBackupResponse] = new CreateBackup(table, backupName, interpreter, QueryDecoder.pure)

  def interpreter: QueryInterpreter[CreateBackup, CreateBackupResponse] = new QueryInterpreter[CreateBackup, CreateBackupResponse] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: CreateBackup[Dec]): F[DynamoException, CreateBackupResponse] = {
      val tableName = query.tableRef.tableName
      ctx.dynamoConfig.maybeLocalUrl match {
        case Some(localEnv) =>
          ctx.logger.info(s"Skipping backup creation for $tableName mock environment: $localEnv, ${query.backupName}") *>
          F.pure(CreateBackupResponse.builder().build())

        case None =>
          ctx.execute("CreateBackup", tableName) {
            client =>
              QueryInterpreter.retryOnFailure(120, 1.second)(client.executeFuture(_.createBackup(query.awsRequest)))(
                DynamoFailureConditions.throwable.resourceInUseOrLimitReached
              )
          } <*
          ctx.logger.info(s"Backup for $tableName was created: ${query.backupName}.")
      }
    }
  }
}
