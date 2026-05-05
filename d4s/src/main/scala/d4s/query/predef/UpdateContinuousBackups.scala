package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.table.DynamoTableRef
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{PointInTimeRecoverySpecification, UpdateContinuousBackupsRequest, UpdateContinuousBackupsResponse}

final case class UpdateContinuousBackups[+Dec](
  table: DynamoTableRef[?],
  backupEnabled: Boolean,
  interpreter: QueryInterpreter[UpdateContinuousBackups, UpdateContinuousBackupsResponse],
  decoder: QueryDecoder[UpdateContinuousBackupsResponse, Dec],
) extends DynamoQuery.WithRequest[UpdateContinuousBackups, Dec] {

  override type Rq  = UpdateContinuousBackupsRequest
  override type Rsp = UpdateContinuousBackupsResponse

  override def withInterpreter(f: QueryInterpreter[UpdateContinuousBackups, UpdateContinuousBackupsResponse]): UpdateContinuousBackups[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[UpdateContinuousBackupsResponse, C1]): UpdateContinuousBackups[C1] =
    copy(decoder = f)

  override def awsRequest: UpdateContinuousBackupsRequest = {
    UpdateContinuousBackupsRequest
      .builder()
      .tableName(table.tableName)
      .pointInTimeRecoverySpecification(
        PointInTimeRecoverySpecification
          .builder()
          .pointInTimeRecoveryEnabled(backupEnabled)
          .build()
      )
      .build()
  }

}

object UpdateContinuousBackups {
  def apply(table: DynamoTableRef[?], backupEnabled: Boolean): UpdateContinuousBackups[UpdateContinuousBackupsResponse] = {
    new UpdateContinuousBackups(table, backupEnabled, interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[UpdateContinuousBackups, UpdateContinuousBackupsResponse] = {
    new QueryInterpreter[UpdateContinuousBackups, UpdateContinuousBackupsResponse] {
      override def interpret[F[+_, +_]: IO2: Temporal2, Dec](
        ctx: QueryCtx[F]
      )(query: UpdateContinuousBackups[Dec]
      ): F[DynamoException, UpdateContinuousBackupsResponse] = {
        val tableName = query.table.tableName
        ctx.dynamoConfig.maybeLocalUrl match {
          case Some(localEnv) =>
            ctx.logger.info(s"Skipping continuous backups update for $tableName in mock environment: $localEnv, ${query.backupEnabled}") *>
            F.pure(UpdateContinuousBackupsResponse.builder().build())

          case None =>
            ctx.execute("UpdateContinuousBackups", tableName)(_.executeFuture(_.updateContinuousBackups(query.awsRequest))) <*
            ctx.logger.info(s"Continuous backup for $tableName were updated: ${query.backupEnabled}.")
        }
      }
    }
  }
}
