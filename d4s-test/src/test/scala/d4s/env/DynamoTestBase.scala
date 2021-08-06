package d4s.env

import d4s.config.{DynamoMeta, ProvisionedThroughputConfig, ProvisioningConfig, TableProvisionedThroughputConfig}
import d4s.env.Models._
import d4s.models.table.TableDef
import d4s.modules.D4SModule
import d4s.test.envs.DynamoTestEnv
import distage.ModuleDef
import izumi.distage.constructors.{ClassConstructor, HasConstructor}
import izumi.distage.model.providers.Functoid
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.test.TkTestBase
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import zio.{IO, ZIO}

abstract class DynamoTestBase[Ctx](implicit val ctor: ClassConstructor[Ctx]) extends TkTestBase[Ctx] with DynamoTestEnv[IO] {

  protected[d4s] final def scopeZIO[R: HasConstructor](f: Ctx => ZIO[R, _, _]): Functoid[IO[_, Unit]] = ctor.provider.map2(HasConstructor[R])(f(_).unit.provide(_))

  override def moduleOverrides: distage.Module = (super.moduleOverrides ++ D4SModule[IO]) overriddenBy new ModuleDef {
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
  }
}
