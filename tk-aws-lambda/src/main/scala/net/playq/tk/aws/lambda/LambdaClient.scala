package net.playq.tk.aws.lambda

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2}
import logstage.LogIO2
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.lambda.LambdaClient.LambdaFunctionConfig
import net.playq.tk.aws.lambda.config.LambdaConfig
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.util.{ClosableFile, process}
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.lambda.model.{CreateFunctionRequest, DeleteFunctionRequest, FunctionCode, InvokeRequest, ResourceNotFoundException, Runtime}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

trait LambdaClient[F[_, _]] {
  def createPublish(deploymentPackageZip: File, config: LambdaFunctionConfig): F[Throwable, String]
  def delete(name: String): F[Throwable, Unit]
  def invoke(name: String, body: String): F[Throwable, String]
  def createCSharpDeploymentPackage(directory: File): Lifecycle[F[Throwable, _], ClosableFile]
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
    override def createPublish(deploymentPackageZip: File, config: LambdaFunctionConfig): F[Throwable, String] = F.pure("arn")
    override def delete(name: String): F[Throwable, Unit]                                                      = F.unit
    override def invoke(name: String, body: String): F[Throwable, String]                                      = F.pure(body)
    override def createCSharpDeploymentPackage(directory: File): Lifecycle[F[Throwable, _], ClosableFile]      = {
      ClosableFile.resourceTempFile()
    }
  }

  final class Impl[F[+_, +_]: IO2](
    component: LambdaComponent[F],
    lambdaConfig: LambdaConfig,
    namespace: AwsNameSpace,
    logger: LogIO2[F],
    service: ServiceName,
  ) extends LambdaClient[F] {

    override def createCSharpDeploymentPackage(packageFolder: File): Lifecycle[F[Throwable, _], ClosableFile] = {
      ClosableFile.resourceTempFile("lambda", ".zip").evalTap {
        zip =>
          for {
            res <- F.sync(process.exec(s"dotnet lambda package ${zip.absolutePath} -pl ${packageFolder.toPath.toAbsolutePath}"))
            _   <- logger.info(s"Lambda Package build result: ${res.output -> "execution"}")
            _   <- F.when(res.exitCode != 0) {
              F.fail(new RuntimeException(s"Deployment package creation was failed with code ${res.exitCode}. Error: ${res.err}. Output: \n${res.output}"))
            }
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

    override def createPublish(deploymentPackageZip: File, config: LambdaFunctionConfig): F[Throwable, String] = {
      import config.*
      val actualName                 = environmentalName(name)
      val req: CreateFunctionRequest = CreateFunctionRequest.builder
        .code(FunctionCode.builder.zipFile(SdkBytes.fromInputStream(Files.newInputStream(deploymentPackageZip.toPath))).build)
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

    private def environmentalName(name: String): String = {
      List(namespace.namespace, service.toOption, Some(name)).flatten.mkString("-")
    }
  }
}
