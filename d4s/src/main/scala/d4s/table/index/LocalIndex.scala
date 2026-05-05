package d4s.table.index

import d4s.table.DynamoKey
import d4s.table.DynamoTableDef.NotInsideDynamoTableDef
import software.amazon.awssdk.services.dynamodb.model.{LocalSecondaryIndex, Projection}

import scala.annotation.unused

final case class LocalIndex[-H, -R](
  name: String,
  key: DynamoKey[H, R],
  projection: Projection,
)(implicit @unused notInDSL: NotInsideDynamoTableDef
) extends TableIndex[H, R] {
  def toAwsIndex: LocalSecondaryIndex = {
    LocalSecondaryIndex
      .builder()
      .indexName(name)
      .keySchema(key.toJava)
      .projection(projection)
      .build()
  }
}
