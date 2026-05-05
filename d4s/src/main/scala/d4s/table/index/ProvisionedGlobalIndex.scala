package d4s.table.index

import d4s.config.ProvisionedThroughputConfig
import d4s.table.DynamoKey
import software.amazon.awssdk.services.dynamodb.model.{CreateGlobalSecondaryIndexAction, Projection}

final case class ProvisionedGlobalIndex[-H, -R](
  name: String,
  key: DynamoKey[H, R],
  projection: Projection,
  provisionedThroughputConfig: ProvisionedThroughputConfig,
) extends TableIndex[H, R] {

  def asCreateAction: CreateGlobalSecondaryIndexAction = {
    provisionedThroughputConfig.configureThroughput {
      CreateGlobalSecondaryIndexAction
        .builder()
        .indexName(name)
        .keySchema(key.toJava)
        .projection(projection)
    }.build()
  }
}
