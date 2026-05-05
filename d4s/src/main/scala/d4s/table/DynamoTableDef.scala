package d4s.table

import d4s.config.{DynamoMeta, ProvisionedThroughputConfig, TableProvisionedThroughputConfig}
import d4s.table.DynamoTableDef.{DynamoTableDiff, InsideDynamoTableDef, indexDiff, provisioningThroughputDiff}
import d4s.table.DynamoTableRef.DynamoTableRefSyntax
import d4s.table.TablePrefix.NamedPrefix
import d4s.table.index.*
import fs2.compat.NotGiven
import net.playq.tk.aws.common.ServiceName
import software.amazon.awssdk.services.dynamodb.model.*

import scala.annotation.unused
import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.chaining.scalaUtilChainingOps

abstract class DynamoTableDef(implicit val dynamoMeta: DynamoMeta) {
  private val globalIndexesState: mutable.Set[GlobalIndex[?, ?]]     = mutable.Set.empty
  private val localIndexesState: mutable.Set[LocalIndex[?, ?]]       = mutable.Set.empty
  private val additionalAttributesState: mutable.Set[DynamoField[?]] = mutable.Set.empty

  val name: String
  val serviceName: ServiceName
  val key: DynamoKey[?, ?]
  val ttlField: Option[String]
  def backupEnabled: Boolean = true

  lazy val provisioning: TableProvisionedThroughputConfig = {
    dynamoMeta.getProvisioning(name)
  }

  final val ref: DynamoTableRef[this.type] = DynamoTableRef(this, None)

  protected final def makeGlobalIndex[H, R](name: String, key: DynamoKey[H, R], projection: Projection): GlobalIndex[H, R] = {
    index.GlobalIndex(name, key, projection)(using NotGiven.value).tap(globalIndexesState.add)
  }

  protected final def makeGlobalIndex[H, R](name: String, key: DynamoKey[H, R]): GlobalIndex[H, R] = {
    makeGlobalIndex(name, key, DynamoTableDef.defaultIndexProjection)
  }

  protected final def makeLocalIndex[H, R](name: String, key: DynamoKey[H, R], projection: Projection): LocalIndex[H, R] = {
    index.LocalIndex(name, key, projection)(using NotGiven.value).tap(localIndexesState.add)
  }

  protected final def makeLocalIndex[H, R](name: String, key: DynamoKey[H, R]): LocalIndex[H, R] = {
    makeLocalIndex(name, key, DynamoTableDef.defaultIndexProjection)
  }

  protected final def addAdditionalAttributes(a: DynamoField[?]*): Unit = {
    additionalAttributesState.addAll(a)
    ()
  }

  final lazy val frozenState = (globalIndexesState.toSet, localIndexesState.toSet, additionalAttributesState.toSet)

  final def fullName(prefix: Option[NamedPrefix]): String = {
    val renderedPrefix      = prefix.fold("")(p => s"${p.prefix}-")
    val renderedServiceName = serviceName match {
      case ServiceName.Empty              => ""
      case ServiceName.Named(serviceName) => s"$serviceName-"
    }
    s"${dynamoMeta.nameSpace}$renderedServiceName$renderedPrefix$name"
  }

  final def tags(prefix: Option[NamedPrefix]): Map[String, String] = {
    dynamoMeta.nameSpace.serviceTags(serviceName) ++ prefix.map(_.toTag).getOrElse(Map.empty)
  }
  final def diff(tableDesc: TableDescription): DynamoTableDiff = {
    DynamoTableDiff(indexDiff(this, tableDesc), provisioningThroughputDiff(this, tableDesc))
  }

  implicit protected def _insideDynamoTableDefDSL: InsideDynamoTableDef = InsideDynamoTableDef.inDSL
}

object DynamoTableDef {
  implicit def toRefSyntax[Def <: DynamoTableDef](table: Def): DynamoTableRefSyntax[Def] = {
    new DynamoTableRefSyntax[Def](table.ref)
  }

  /** Calling any one of these methods will finalize the DSL state and nullify all subsequent mutations */
  implicit final class DynamoTableDefFreezeSyntax(private val tableDef: DynamoTableDef) extends AnyVal {
    def globalIndexes(implicit @unused notInDSL: NotInsideDynamoTableDef): Set[GlobalIndex[?, ?]]     = tableDef.frozenState._1
    def localIndexes(implicit @unused notInDSL: NotInsideDynamoTableDef): Set[LocalIndex[?, ?]]       = tableDef.frozenState._2
    def additionalAttributes(implicit @unused notInDSL: NotInsideDynamoTableDef): Set[DynamoField[?]] = tableDef.frozenState._3
  }

  private def defaultIndexProjection: Projection = {
    Projection.builder().projectionType(ProjectionType.ALL).build()
  }

  type NotInsideDynamoTableDef = NotGiven[InsideDynamoTableDef]

  sealed abstract class InsideDynamoTableDef
  object InsideDynamoTableDef {
    def inDSL: InsideDynamoTableDef = null
  }

  final case class DynamoTableDiff(
    indexToUpdate: IndexToUpdate,
    provisioning: Option[ProvisionedThroughputConfig],
  )

  private def indexDiff(table: DynamoTableDef, tableDesc: TableDescription): IndexToUpdate = {
    import scala.jdk.CollectionConverters.*

    val oldSecondaryIndexes = tableDesc.globalSecondaryIndexes().asScala.map(i => i.indexName -> i).toMap
    val currentIndexNames   = table.globalIndexes.map(_.name).toList
    val oldIndexNames       = oldSecondaryIndexes.keys.toList

    val namesToCreate = currentIndexNames diff oldIndexNames
    val namesToDelete = oldIndexNames diff currentIndexNames

    val indexesToUpd = currentIndexNames.filter {
      name =>
        oldSecondaryIndexes.get(name) match {
          case None        => false
          case Some(value) =>
            val provisioning = table.provisioning.getIndexProvisioning(name)
            provisioning.read != (value.provisionedThroughput().readCapacityUnits(): Long) ||
            provisioning.write != (value.provisionedThroughput().writeCapacityUnits(): Long)
        }
    }

    val create: Set[ProvisionedGlobalIndex[?, ?]] = table.globalIndexes
      .filter(i => namesToCreate.contains(i.name))
      .map(i => ProvisionedGlobalIndex(i.name, i.key, i.projection, table.provisioning.getIndexProvisioning(i.name)))

    val modify = table.globalIndexes
      .filter(indexesToUpd.contains)
      .map(i => GlobalIndexUpdate(i.name, table.provisioning.getIndexProvisioning(i.name)))

    val delete = tableDesc.globalSecondaryIndexes().asScala.filter(i => namesToDelete.contains(i.indexName())).map(_.indexName()).toSet

    index.IndexToUpdate(create, modify, delete)
  }

  private def provisioningThroughputDiff(table: DynamoTableDef, tableDesc: TableDescription): Option[ProvisionedThroughputConfig] = {
    val oldConfig = ProvisionedThroughputConfig(
      tableDesc.provisionedThroughput().readCapacityUnits(),
      tableDesc.provisionedThroughput().writeCapacityUnits(),
      Option(tableDesc.billingModeSummary()).flatMap(b => Option(b.billingMode())).getOrElse(BillingMode.UNKNOWN_TO_SDK_VERSION),
    )

    val currConfig = table.provisioning.tableProvisioning
    if (currConfig.read != oldConfig.read || currConfig.write != oldConfig.write)
      Some(currConfig)
    else
      None
  }
}
