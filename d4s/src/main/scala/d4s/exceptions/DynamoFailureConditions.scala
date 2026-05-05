package d4s.exceptions

import software.amazon.awssdk.services.dynamodb.model.*

object DynamoFailureConditions {
  object throwable {
    def tableNotFound: PartialFunction[Throwable, Boolean] = {
      case _: ResourceNotFoundException | _: ResourceInUseException | _: TableNotFoundException => true
    }
    def backupUnavailable: PartialFunction[Throwable, Boolean] = {
      case _: ContinuousBackupsUnavailableException => true
    }
    def resourceInUse: PartialFunction[Throwable, Boolean] = {
      case _: ResourceInUseException => true
    }
    def operationLimitReached: PartialFunction[Throwable, Boolean] = {
      case l: LimitExceededException => l.getMessage.contains("tables can be created, updated, or deleted simultaneously")
    }
    def resourceInUseOrLimitReached: PartialFunction[Throwable, Boolean] = {
      resourceInUse.orElse(operationLimitReached)
    }
  }

  object d4s {
    def tableNotFound: PartialFunction[DynamoException, Boolean] = {
      throwable.tableNotFound.compose { case d => d.cause }
    }
    def backupUnavailable: PartialFunction[DynamoException, Boolean] = {
      throwable.backupUnavailable.compose { case d => d.cause }
    }
    def operationLimitReached: PartialFunction[DynamoException, Boolean] = {
      throwable.operationLimitReached.compose { case d => d.cause }
    }
    def resourceInUse: PartialFunction[DynamoException, Boolean] = {
      throwable.resourceInUse.compose { case d => d.cause }
    }
    def resourceInUseOrLimitReached: PartialFunction[DynamoException, Boolean] = {
      throwable.resourceInUseOrLimitReached.compose { case d => d.cause }
    }
  }
}
