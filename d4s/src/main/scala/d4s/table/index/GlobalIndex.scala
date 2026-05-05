package d4s.table.index

import d4s.config.{ProvisionedThroughputConfig, TableProvisionedThroughputConfig}
import d4s.table.DynamoKey
import d4s.table.DynamoTableDef.NotInsideDynamoTableDef
import software.amazon.awssdk.services.dynamodb.model.{GlobalSecondaryIndex, Projection}

import scala.annotation.unused

final case class GlobalIndex[-H, -R](
  name: String,
  key: DynamoKey[H, R],
  projection: Projection,
)(implicit @unused notInDSL: NotInsideDynamoTableDef
) extends TableIndex[H, R] {
  def toAwsIndex(provisionedThroughputConfig: ProvisionedThroughputConfig): GlobalSecondaryIndex = {
    provisionedThroughputConfig
      .configureThroughput(
        GlobalSecondaryIndex
          .builder()
          .indexName(name)
          .keySchema(key.toJava)
          .projection(projection)
      )
      .build()
  }

  def toProvisionedIndex(cfg: TableProvisionedThroughputConfig): ProvisionedGlobalIndex[H, R] = {
    ProvisionedGlobalIndex(name, key, projection, cfg.getIndexProvisioning(name))
  }
}
