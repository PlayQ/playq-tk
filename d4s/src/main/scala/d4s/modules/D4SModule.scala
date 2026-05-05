package d4s.modules

import d4s.*
import d4s.config.*
import d4s.docker.DynamoDocker
import d4s.services.*
import d4s.table.DynamoTableDef
import distage.config.ConfigModuleDef
import distage.{ModuleDef, TagKK}
import izumi.fundamentals.platform.integration.PortCheck
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.health.HealthChecker

import scala.concurrent.duration.*

final class D4SModule[F[+_, +_]: TagKK] extends ModuleDef {
  include(D4SModule.base[F])
  include(D4SModule.configs)
}

object D4SModule {
  def apply[F[+_, +_]: TagKK]: ModuleDef = new D4SModule[F]

  def base[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    make[DynamoComponent].fromResource[DynamoComponent.Resource[F]]
    make[DynamoClient[F]].from[DynamoClient.Impl[F]]
    make[Dynamo[F]].from[Dynamo.Impl[F]].addDependency[DynamoHealthChecker[F]]

    make[DynamoHealthChecker[F]]
    many[HealthChecker[F]].weak[DynamoHealthChecker[F]]

    make[DynamoTablesService[F]].from[DynamoTablesService.Impl[F]]
    make[DynamoSetupService[F]].fromResource[DynamoSetupService[F]]

    many[DynamoTableDef]

    make[PortCheck].named("dynamo-port").from {
      new PortCheck(3.seconds)
    }

    include(DynamoDocker.module[F[Throwable, _]]("aws.dynamo"))
  }

  val configs: ConfigModuleDef = new ConfigModuleDef {
    make[DynamoMeta].from {
      (provisioningConf: ProvisioningConfig, namespace: AwsNameSpace, dynamoConf: DynamoConfig) =>
        dynamoConf.maybeLocalUrl match {
          case Some(_) => DynamoMeta(ProvisioningConfig(ProvisionedThroughputConfig.minimal, Nil), namespace, None)
          case None    => DynamoMeta(provisioningConf, namespace, dynamoConf.backupEnabled)
        }
    }

    makeConfig[DynamoBatchConfig]("aws.dynamo")
    makeConfig[DynamoTransactionConfig]("aws.dynamo")
    makeConfig[ProvisioningConfig]("aws.dynamo.provisioning")
  }
}
