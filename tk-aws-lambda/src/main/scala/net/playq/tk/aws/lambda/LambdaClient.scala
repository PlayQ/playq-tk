package net.playq.tk.aws.lambda

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2}
import logstage.LogIO2
import net.playq.tk.aws.common.ServiceName
import LambdaClient.LambdaFunctionConfig
import net.playq.aws.tagging.AwsNameSpace
import net.playq.tk.util.ManagedFile
import net.playq.tk.aws.lambda.config.LambdaConfig
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.lambda.model.{CreateFunctionRequest, DeleteFunctionRequest, FunctionCode, InvokeRequest, ResourceNotFoundException, Runtime}

import java.nio.charset.StandardCharsets
import java.nio.file.Files

trait LambdaClient[F[_, _]] {
  def createPublish(deploymentPackageZip: ManagedFile, config: LambdaFunctionConfig): F[Throwable, String]
  def delete(name: String): F[Throwable, Unit]
  def invoke(name: String, body: String): F[Throwable, String]
  def createCSharpDeploymentPackage(directory: ManagedFile): Lifecycle[F[Throwable, ?], ManagedFile]
}

object LambdaClient {
  final case class LambdaFunctionConfig(
    name: String,
    handlerName: String,
    runtime: Runtime,
    memorySize: Int,
    timeout: Int = 30,
  )

  final class Dummy[F[+_, +_]: IO2] extends LambdaClient[F] {
    override def createPublish(deploymentPackageZip: ManagedFile, config: LambdaFunctionConfig): F[Throwable, String] = F.pure("arn")
    override def delete(name: String): F[Throwable, Unit]                                                             = F.unit
    override def invoke(name: String, body: String): F[Throwable, String]                                             = F.pure(body)
    override def createCSharpDeploymentPackage(directory: ManagedFile): Lifecycle[F[Throwable, ?], ManagedFile] = {
      ManagedFile.managedFile()
    }
  }

  final class Impl[F[+_, +_]: IO2](
    component: LambdaComponent[F],
    lambdaConfig: LambdaConfig,
    namespace: AwsNameSpace,
    logger: LogIO2[F],
    service: ServiceName,
  ) extends LambdaClient[F] {

    override def createCSharpDeploymentPackage(directory: ManagedFile): Lifecycle[F[Throwable, ?], ManagedFile] = {
      import sys.process._
      ManagedFile.managedFile("lambda", ".zip").evalTap {
        packageFolder =>
          for {
            res <- F.syncThrowable(Process(s"dotnet lambda package ${packageFolder.absolutePath} -pl ${directory.absolutePath}").!)
            _   <- F.when(res != 0)(F.fail(new RuntimeException(s"Deployment package creation was failed with code $res")))
          } yield ()
      }
    }

    override def invoke(name: String, body: String): F[Throwable, String] = {
      val actualName = environmentalName(name)
      val req        = InvokeRequest.builder.functionName(actualName).payload(SdkBytes.fromString(body, StandardCharsets.UTF_8)).build
      logger.info(s"Going to invoke ${actualName -> "LambdaFunction"} with $body.") *>
      component.rawRequest(_.invoke(req)).map {
        res =>
          new String(res.payload.asByteArray())
      }
    }

    override def delete(name: String): F[Throwable, Unit] = {
      val actualName = environmentalName(name)
      logger.info(s"Going to delete ${actualName -> "LambdaFunction"}.") *>
      component.rawRequest(_.deleteFunction(DeleteFunctionRequest.builder.functionName(actualName).build)).void.catchSome {
        case _: ResourceNotFoundException => F.unit
      }
    }

    override def createPublish(deploymentPackageZip: ManagedFile, config: LambdaFunctionConfig): F[Throwable, String] = {
      import config._
      val actualName = environmentalName(name)
      val req: CreateFunctionRequest = CreateFunctionRequest.builder
        .code(FunctionCode.builder.zipFile(SdkBytes.fromInputStream(Files.newInputStream(deploymentPackageZip.file.toPath))).build)
        .functionName(actualName)
        .handler(handlerName)
        .memorySize(memorySize)
        .runtime(runtime)
        .publish(true)
        .role(lambdaConfig.roleArn)
        .timeout(timeout)
        .build

      logger.info(s"Going to create and publish ${actualName -> "LambdaFunction"} with $config.") *>
      component.rawRequest(_.createFunction(req)).map {
        _.functionArn
      }
    }

    @inline private def environmentalName(name: String): String = {
      namespace.namespace.fold(s"${service.serviceName}-$name") {
        namespace => s"$namespace-${service.serviceName}-$name"
      }
    }
  }
}
