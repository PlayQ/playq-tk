package d4s.config

import izumi.distage.config.codec.{ConfigMetaBasicType, ConfigMetaType, DIConfigMeta, DIConfigReader}
import software.amazon.awssdk.services.dynamodb.model.BillingMode

final case class ProvisioningConfig(
  default: ProvisionedThroughputConfig,
  tables: List[TableProvisionedThroughputConfig],
)

object ProvisioningConfig {
  implicit lazy val billingModeMeta: DIConfigMeta[BillingMode] = DIConfigMeta(ConfigMetaType.TBasic(ConfigMetaBasicType.TString))
  implicit lazy val reader: DIConfigReader[ProvisioningConfig] = DIConfigReader.derived
  implicit lazy val mate: DIConfigMeta[ProvisioningConfig]     = DIConfigMeta.derived
}
