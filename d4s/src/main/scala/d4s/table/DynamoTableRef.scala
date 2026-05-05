package d4s.table

import d4s.codecs.D4SEncoder
import d4s.config.TableProvisionedThroughputConfig
import d4s.query.models.ItemWithTtl
import d4s.query.predef.*
import d4s.table.TablePrefix.NamedPrefix
import d4s.table.index.{GlobalIndex, LocalIndex, TableIndex}
import net.playq.tk.aws.tagging.SharedTags
import software.amazon.awssdk.services.dynamodb.model.*

import java.time.ZonedDateTime

final case class DynamoTableRef[+Def](
  table: Def & DynamoTableDef,
  prefix: Option[NamedPrefix] = None,
) {

  lazy val tableName: String         = table.fullName(prefix)
  lazy val tags: Map[String, String] = table.tags(prefix)

  def key: DynamoKey[?, ?]                           = table.key
  def ttlField: Option[String]                       = table.ttlField
  def backupEnabled: Boolean                         = table.backupEnabled
  def provisioning: TableProvisionedThroughputConfig = table.provisioning

  def globalIndexes: Set[GlobalIndex[?, ?]]     = table.globalIndexes
  def localIndexes: Set[LocalIndex[?, ?]]       = table.localIndexes
  def additionalAttributes: Set[DynamoField[?]] = table.additionalAttributes

  def withPrefix[TP: TablePrefix](newPrefix: TP): DynamoTableRef[Def] = {
    copy(prefix = Some(TablePrefix[TP].asTablePrefix(newPrefix)))
  }
}

object DynamoTableRef {

  implicit final class DynamoTableRefSyntax[Def](protected val table: DynamoTableRef[Def]) extends AnyVal with DynamoTableRefSyntaxLowPriority[Def] {
    def getItem: GetItem[GetItemResponse]                                           = GetItem(table)
    def getItem[Item: D4SEncoder.AsObject](keyItem: Item): GetItem[GetItemResponse] = GetItem(table).withKeyItem(keyItem)
    def getItem(key: Map[String, AttributeValue]): GetItem[GetItemResponse]         = GetItem(table).withKey(key)

    def putItem: PutItem[PutItemResponse]                                        = PutItem(table)
    def putItem[Item: D4SEncoder.AsObject](item: Item): PutItem[PutItemResponse] = PutItem(table).withItem(item)
    def putItem(key: Map[String, AttributeValue]): PutItem[PutItemResponse]      = PutItem(table).withItemAttributeValues(key)

    def deleteItem: DeleteItem[DeleteItemResponse]                                           = DeleteItem(table)
    def deleteItem[Item: D4SEncoder.AsObject](keyItem: Item): DeleteItem[DeleteItemResponse] = DeleteItem(table).withKeyItem(keyItem)
    def deleteItem(key: Map[String, AttributeValue]): DeleteItem[DeleteItemResponse]         = DeleteItem(table).withKey(key)

    def updateItem: UpdateItem[UpdateItemResponse]                                        = UpdateItem(table)
    def updateItem[Item: D4SEncoder.AsObject](item: Item): UpdateItem[UpdateItemResponse] = UpdateItem(table).withItem(item)
    def updateItem(key: Map[String, AttributeValue]): UpdateItem[UpdateItemResponse]      = UpdateItem(table).withItemAttributeValues(key)

    def deleteItemBatch: DeleteItemBatch[List[BatchWriteItemResponse]]                                               = DeleteItemBatch(table)
    def deleteItemBatch[I: D4SEncoder.AsObject](deleteBatch: List[I]): DeleteItemBatch[List[BatchWriteItemResponse]] = DeleteItemBatch(table).withBatch(deleteBatch)

    def putItemBatch: PutItemBatch[List[BatchWriteItemResponse]]                                                           = PutItemBatch(table)
    def putItemBatch[I: D4SEncoder.AsObject](putBatch: Iterable[I]): PutItemBatch[List[BatchWriteItemResponse]]            = PutItemBatch(table).withBatch(putBatch)
    def putItemBatch[I: D4SEncoder.AsObject](putBatch: Iterable[I], ttl: Long): PutItemBatch[List[BatchWriteItemResponse]] = PutItemBatch(table).withBatch(putBatch, ttl)
    def putItemBatch[I: D4SEncoder.AsObject](putBatch: Iterable[I], ttl: ZonedDateTime): PutItemBatch[List[BatchWriteItemResponse]] =
      PutItemBatch(table).withBatch(putBatch, ttl)
    def putItemWithTtlBatch[I: D4SEncoder.AsObject](putBatch: Iterable[ItemWithTtl[I]]): PutItemBatch[List[BatchWriteItemResponse]] =
      PutItemBatch(table).withBatchTtl(putBatch)

    def getItemBatch: GetItemBatch[List[BatchGetItemResponse]]                                            = GetItemBatch(table)
    def getItemBatch[I: D4SEncoder.AsObject](getBatch: List[I]): GetItemBatch[List[BatchGetItemResponse]] = GetItemBatch(table).withBatch(getBatch)

    def scan: Scan[ScanResponse]                          = Scan(table)
    def scan(index: TableIndex[?, ?]): Scan[ScanResponse] = Scan(table).withIndex(index)

    def update: UpdateTable[UpdateTableResponse]                                                         = UpdateTable(table)
    def describe: DescribeTable[DescribeTableResponse]                                                   = DescribeTable(table)
    def updateTags(arn: String, tagsToUpdate: Map[String, String]): UpdateTableTags[TagResourceResponse] = UpdateTableTags(table, arn).withTags(tagsToUpdate)
    def getTags(arn: String): ListTableTags[ListTagsOfResourceResponse]                                  = ListTableTags(table, arn)
    def markForDeletion(arn: String): UpdateTableTags[TagResourceResponse] = UpdateTableTags(table, arn).withTags(Map(SharedTags.markedForDeletion))

    def query: Query[QueryResponse]                                                         = Query(table)
    def query(key: Map[String, AttributeValue]): Query[QueryResponse]                       = Query(table).withKey(key)
    def query(index: TableIndex[?, ?]): Query[QueryResponse]                                = Query(table).withIndex(index)
    def query[H](index: TableIndex[H, ?], hashKey: H): Query[QueryResponse]                 = Query(table).withIndex(index).withKeyField(index.key.hashKey)(hashKey)
    def query[H, R](index: TableIndex[H, R], hashKey: H, rangeKey: R): Query[QueryResponse] = Query(table).withIndex(index).withKey(index.key.bind(hashKey, rangeKey))

    def queryDeleteBatch: QueryDeleteBatch[List[BatchWriteItemResponse]]                                   = QueryDeleteBatch(table)
    def queryDeleteBatch(maxParallelDeletes: Int): QueryDeleteBatch[List[BatchWriteItemResponse]]          = QueryDeleteBatch(table).withParallelism(maxParallelDeletes)
    def queryDeleteBatch(index: TableIndex[?, ?]): QueryDeleteBatch[List[BatchWriteItemResponse]]          = QueryDeleteBatch(table).withIndex(index)
    def queryDeleteBatch(key: Map[String, AttributeValue]): QueryDeleteBatch[List[BatchWriteItemResponse]] = QueryDeleteBatch(table).withKey(key)
    def queryDeleteBatch[H](index: TableIndex[H, ?], hashKey: H): QueryDeleteBatch[List[BatchWriteItemResponse]] =
      QueryDeleteBatch(table).withIndex(index).withKeyField(index.key.hashKey)(hashKey)
    def queryDeleteBatch[H, R](index: TableIndex[H, R], hashKey: H, rangeKey: R): QueryDeleteBatch[List[BatchWriteItemResponse]] =
      QueryDeleteBatch(table).withIndex(index).withKey(index.key.bind(hashKey, rangeKey))

    def scanDeleteBatch: ScanDeleteBatch[List[BatchWriteItemResponse]]                          = ScanDeleteBatch(table)
    def scanDeleteBatch(maxParallelDeletes: Int): ScanDeleteBatch[List[BatchWriteItemResponse]] = ScanDeleteBatch(table).withParallelism(maxParallelDeletes)
  }

  sealed trait DynamoTableRefSyntaxLowPriority[Def] extends Any {
    protected def table: DynamoTableRef[Def]

    final def getItem[H](hashKey: H)(implicit ev: WithHashKey[Def, H]): GetItem[GetItemResponse] =
      GetItem(table).withKey(ev(table.table).key.bind(hashKey))
    final def getItem[H, R](hashKey: H, rangeKey: R)(implicit ev: WithHashRangeKeys[Def, H, R]): GetItem[GetItemResponse] =
      GetItem(table).withKey(ev(table.table).key.bind(hashKey, rangeKey))

    final def putItem[H](hashKey: H)(implicit ev: WithHashKey[Def, H]): PutItem[PutItemResponse] =
      PutItem(table).withItemAttributeValues(ev(table.table).key.bind(hashKey))
    final def putItem[H, R](hashKey: H, rangeKey: R)(implicit ev: WithHashRangeKeys[Def, H, R]): PutItem[PutItemResponse] =
      PutItem(table).withItemAttributeValues(ev(table.table).key.bind(hashKey, rangeKey))

    final def deleteItem[H](hashKey: H)(implicit ev: WithHashKey[Def, H]): DeleteItem[DeleteItemResponse] =
      DeleteItem(table).withKey(ev(table.table).key.bind(hashKey))
    final def deleteItem[H, R](hashKey: H, rangeKey: R)(implicit ev: WithHashRangeKeys[Def, H, R]): DeleteItem[DeleteItemResponse] =
      DeleteItem(table).withKey(ev(table.table).key.bind(hashKey, rangeKey))

    final def updateItem[H](hashKey: H)(implicit ev: WithHashKey[Def, H]): UpdateItem[UpdateItemResponse] =
      UpdateItem(table).withItemAttributeValues(ev(table.table).key.bind(hashKey))
    final def updateItem[H, R](hashKey: H, rangeKey: R)(implicit ev: WithHashRangeKeys[Def, H, R]): UpdateItem[UpdateItemResponse] =
      UpdateItem(table).withItemAttributeValues(ev(table.table).key.bind(hashKey, rangeKey))

    final def query[H](hashKey: H)(implicit ev: WithHashKey[Def, H]): Query[QueryResponse] =
      Query(table).withKey(ev(table.table).key.bind(hashKey))
    final def query[H, R](hashKey: H, rangeKey: R)(implicit ev: WithHashRangeKeys[Def, H, R]): Query[QueryResponse] =
      Query(table).withKey(ev(table.table).key.bind(hashKey, rangeKey))

    final def queryDeleteBatch[H](hashKey: H)(implicit ev: WithHashKey[Def, H]): QueryDeleteBatch[List[BatchWriteItemResponse]] =
      QueryDeleteBatch(table).withKey(ev(table.table).key.bind(hashKey))
    final def queryDeleteBatch[H, R](hashKey: H, rangeKey: R)(implicit ev: WithHashRangeKeys[Def, H, R]): QueryDeleteBatch[List[BatchWriteItemResponse]] =
      QueryDeleteBatch(table).withKey(ev(table.table).key.bind(hashKey, rangeKey))
  }

  private type WithHashKey[Def, H]          = Def <:< DynamoTableDef { val key: DynamoKey[H, Nothing] }
  private type WithHashRangeKeys[Def, H, R] = Def <:< DynamoTableDef { val key: DynamoKey[H, R] }

}
