package d4s.config

import d4s.config.ProvisionedThroughputConfig.{HasBillingMode, HasProvisionedThroughput}
import software.amazon.awssdk.services.dynamodb.model.*

final case class ProvisionedThroughputConfig(
  read: Long,
  write: Long,
  mode: BillingMode,
) {
  def configureThroughput[T](builder: T)(implicit ev: HasProvisionedThroughput[T]): T = {
    mode match {
      case BillingMode.PROVISIONED =>
        ev.provisionedThroughput(
          builder,
          ProvisionedThroughput
            .builder()
            .readCapacityUnits(read)
            .writeCapacityUnits(write)
            .build(),
        )
      case BillingMode.PAY_PER_REQUEST =>
        builder
      case BillingMode.UNKNOWN_TO_SDK_VERSION =>
        builder
    }
  }

  def configureBilling[T](builder: T)(implicit ev: HasBillingMode[T]): T = {
    ev.billingMode(builder, mode)
  }

  def configure[T: HasProvisionedThroughput: HasBillingMode](builder: T): T = {
    configureBilling(configureThroughput(builder))
  }

  override def toString: String = s"$mode:${write}W:${read}R"
}

object ProvisionedThroughputConfig {
  final val minimal = ProvisionedThroughputConfig(1L, 1L, BillingMode.PROVISIONED)

  trait HasBillingMode[A] {
    def billingMode(a: A, billingMode: BillingMode): A
  }
  object HasBillingMode {
    implicit val hasBillingModeCreateTableRequest: HasBillingMode[CreateTableRequest.Builder] = _.billingMode(_)
    implicit val hasBillingModeUpdateTableRequest: HasBillingMode[UpdateTableRequest.Builder] = _.billingMode(_)
  }

  trait HasProvisionedThroughput[A] {
    def provisionedThroughput(a: A, provisionedThroughput: ProvisionedThroughput): A
  }
  object HasProvisionedThroughput {
    implicit val hasProvisionedThroughputCreateTableRequest: HasProvisionedThroughput[CreateTableRequest.Builder]                             = _.provisionedThroughput(_)
    implicit val hasProvisionedThroughputUpdateTableRequest: HasProvisionedThroughput[UpdateTableRequest.Builder]                             = _.provisionedThroughput(_)
    implicit val hasProvisionedThroughputGlobalSecondaryIndex: HasProvisionedThroughput[GlobalSecondaryIndex.Builder]                         = _.provisionedThroughput(_)
    implicit val hasProvisionedThroughputUpdateGlobalSecondaryIndexAction: HasProvisionedThroughput[UpdateGlobalSecondaryIndexAction.Builder] = _.provisionedThroughput(_)
    implicit val hasProvisionedThroughputCreateGlobalSecondaryIndexAction: HasProvisionedThroughput[CreateGlobalSecondaryIndexAction.Builder] = _.provisionedThroughput(_)
  }
}
