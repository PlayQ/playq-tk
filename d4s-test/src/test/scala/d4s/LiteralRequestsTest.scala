package d4s

import d4s.config.{DynamoMeta, ProvisionedThroughputConfig, ProvisioningConfig, TableProvisionedThroughputConfig}
import d4s.env.Models._
import d4s.models.table.TableDef
import d4s.modules.D4SModule
import distage.ModuleDef
import izumi.distage.plugins.{PluginConfig, PluginDef}
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec3}
import izumi.functional.bio.F
import logstage.LogIO2
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.metrics.modules.DummyMetricsModule
import net.playq.tk.plugins.{AwsTagsModule, TkDockerPlugin}
import net.playq.tk.test.rnd.TkRndBase
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import zio.{IO, Task, ZIO}

@SuppressWarnings(Array("DoubleNegation"))
final class LiteralRequestsTest extends Spec3[ZIO] with TkRndBase with AssertZIO {
  "literal tuple codecs" should {
    "perform put" in {
      (testTable4: TestTable4) =>
        for {
          key   <- F.random[Int].map(i => s"key$i")
          _     <- d4z.runUnrecorded(testTable4.table.putItem.withItem(("key1", key)).withItem(("value1", "f3")))
          get    = testTable4.table.getItem(("key1", key)).decodeItem[("value1", String)]
          read1 <- d4z.runUnrecorded(get)
          _     <- assertIO(read1 contains ("value1" -> "f3"))
        } yield ()
    }
  }

  override def config: TestConfig = super.config.copy(
    pluginConfig = PluginConfig.const(LiteralRequestsTest.D4STestPlugin),
    moduleOverrides = super.config.moduleOverrides overriddenBy new ModuleDef {
      make[DynamoMeta].from {
        namespace: AwsNameSpace =>
          val cfgDefault: ProvisionedThroughputConfig = ProvisionedThroughputConfig.minimal
          val cfgForTable1                            = ProvisionedThroughputConfig(2L, 2L, BillingMode.PROVISIONED)
          DynamoMeta(ProvisioningConfig(cfgDefault, List(TableProvisionedThroughputConfig("table1", cfgForTable1, Nil))), namespace, None)
      }

      make[InterpreterTestTable]
      make[TestTable1]
      make[TestTable2]
      make[TestTable3]
      make[TestTable4]
      make[UpdatedTestTable]
      make[UpdatedTestTable1]
      make[UpdatedTestTable2]

      many[TableDef]
        .ref[TestTable1]
        .ref[TestTable2]
        .ref[TestTable3]
        .ref[TestTable4]
        .ref[UpdatedTestTable]
        .ref[InterpreterTestTable]
    },
    configBaseName = "test",
  )
}

object LiteralRequestsTest {
  object D4STestPlugin extends PluginDef {
    include(AwsTagsModule)
    include(DummyMetricsModule[IO])
    include(D4SModule[IO])
    include(TkDockerPlugin.module[Task])
    make[LogIO2[IO]].from(LogIO2.fromLogger[IO] _)
  }
}
