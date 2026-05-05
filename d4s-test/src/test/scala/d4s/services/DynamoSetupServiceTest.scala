package d4s.services

import d4s.Dynamo
import d4s.config.DynamoMeta
import d4s.query.predef.ListTables
import d4s.services.DynamoSetupServiceTest.Ctx
import d4s.table.DynamoTableDef
import d4s.test.DynamoTestBase
import d4s.test.Models.*
import distage.{DefaultModule2, TagKK}
import izumi.functional.bio.{Clock2, F, IO2}
import zio.IO

import java.util.UUID
import scala.jdk.CollectionConverters.CollectionHasAsScala

final class DynamoSetupServiceTest extends DynamoSetupServiceTestBase[IO]

object DynamoSetupServiceTest {
  final case class Ctx[F[+_, +_]](
    meta: DynamoMeta,
    ddl: DynamoSetupService[F],
    dynamo: Dynamo[F],
    dynamoClient: DynamoClient[F],
    tablesService: DynamoTablesService[F],
    instances: Set[DynamoTableDef],
    testTable: TestTable1,
    clock: Clock2[F],
  )
}

abstract class DynamoSetupServiceTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends DynamoTestBase[F, Ctx[F]] {

  "DynamoDdlService" must {
    "perform ddl up" in bound {
      ctx =>
        import ctx.*
        for {
          prefix    <- F.sync(UUID.randomUUID())
          _         <- assertIO(instances.nonEmpty)
          randomKey  = Table1Key(UUID.randomUUID().toString, UUID.randomUUID().toString)
          randomItem = Table1Item(UUID.randomUUID().toString, UUID.randomUUID().toString)

          put   = testTable.putItem.withItems(randomKey, randomItem).prefixed(prefix)
          _    <- dynamo.run(put)
          get   = testTable.getItem(randomKey).prefixed(prefix)
          item <- dynamo.run(get.decodeItem[Table1Item])
          _    <- assertIO(item.get == randomItem)

          // all bind tables should be created, including prefixed table from `put`
          createdTables  = instances.map(_.fullName(None)) + put.tableRef.tableName
          tablesFromAws <- dynamo.run(ListTables().decode(_.tableNames().asScala).readAll.map(_.toSet))
          _             <- assertIO(createdTables.diff(tablesFromAws).isEmpty)

          // table should have 4 tags: environment, deployment, service, prefix
          _ <- assertIO(testTable.ref.withPrefix(prefix).updateTags("", Map.empty).requestedTags.size == 4)
          _ <- assertIO(testTable.updateTags("", Map.empty).requestedTags.size == 3)
        } yield ()
    }

    "perform base crud" in bound {
      ctx =>
        import ctx.*
        for {
          prefix    <- F.sync(UUID.randomUUID())
          _         <- assertIO(instances.nonEmpty)
          randomKey  = Table1Key(UUID.randomUUID().toString, UUID.randomUUID().toString)
          randomItem = Table1Item(UUID.randomUUID().toString, UUID.randomUUID().toString)

          _              <- tablesService.setupPrefixed(prefix)(instances)
          createdTables   = instances.map(_.ref.withPrefix(prefix).tableName)
          tablesFromAws1 <- dynamo.run(ListTables().decode(_.tableNames().asScala).readAll.map(_.toSet))
          _              <- assertIO(createdTables.removedAll(tablesFromAws1).isEmpty)

          put   = testTable.putItem.withItems(randomKey, randomItem).prefixed(prefix)
          _    <- dynamo.run(put)
          get   = testTable.getItem(randomKey).prefixed(prefix).decodeItem[Table1Item]
          item <- dynamo.run(get)
          _    <- assertIO(item.get == randomItem)

          now <- clock.nowZoned()
          _   <- tablesService.backupPrefixed(prefix)(now, instances)
          _   <- tablesService.deletePrefixed(prefix)(instances)

          tablesFromAws2 <- dynamo.run(ListTables().decode(_.tableNames().asScala).readAll.map(_.toSet))
          _              <- assertIO(createdTables.removedAll(tablesFromAws2) == createdTables)

          _              <- tablesService.setupPrefixed(prefix)(instances)
          createdTables2  = instances.map(_.ref.withPrefix(prefix).tableName)
          tablesFromAws3 <- dynamo.run(ListTables().decode(_.tableNames().asScala).readAll.map(_.toSet))
          _              <- assertIO(createdTables2.removedAll(tablesFromAws3).isEmpty)

          _              <- tablesService.backupAndDeletePrefixed(prefix)(now, instances)
          tablesFromAws4 <- dynamo.run(ListTables().decode(_.tableNames().asScala).readAll.map(_.toSet))
          _              <- assertIO(createdTables2.removedAll(tablesFromAws4) == createdTables2)

          _ <- tablesService.backupAndDeletePrefixed(prefix)(now, instances)
        } yield ()
    }
  }

//  override def config: TestConfig = super.config.copy(activation = super.config.activation + (Scene -> Scene.Provided))
}
