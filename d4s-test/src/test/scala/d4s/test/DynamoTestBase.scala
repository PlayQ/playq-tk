package d4s.test

import d4s.config.{DynamoMeta, ProvisionedThroughputConfig, ProvisioningConfig, TableProvisionedThroughputConfig}
import d4s.test.Models.*
import d4s.table.DynamoTableDef
import d4s.test.envs.DynamoTestEnv
import distage.{DefaultModule2, ModuleDef, TagKK}
import izumi.distage.constructors.ClassConstructor
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import izumi.functional.bio.Functor2
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.test.{TkTestBaseConfig, TkTestBaseCtx}
import software.amazon.awssdk.services.dynamodb.model.BillingMode

abstract class DynamoTestBase[F[+_, +_]: Functor2: TagKK: DefaultModule2, Ctx: ClassConstructor] extends TkTestBaseCtx[F, Ctx] with DynamoTestConfig[F]

abstract class DynamoTestBaseZIO extends SpecZIO with AssertZIO with DynamoTestConfig[zio.IO]

trait DynamoTestConfig[F[+_, +_]] extends TkTestBaseConfig with DynamoTestEnv[F] {

  override def moduleOverrides: distage.Module = super.moduleOverrides overriddenBy new ModuleDef {
    make[DynamoMeta].from {
      (namespace: AwsNameSpace) =>
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

    many[DynamoTableDef]
      .ref[TestTable1]
      .ref[TestTable2]
      .ref[TestTable3]
      .ref[TestTable4]
      .ref[UpdatedTestTable]
      .ref[InterpreterTestTable]
  }

}
