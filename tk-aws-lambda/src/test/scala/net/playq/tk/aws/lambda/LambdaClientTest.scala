package net.playq.tk.aws.lambda

import distage.{Lifecycle, TagKK}
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{F, IO2}
import logstage.LogIO2
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.lambda.LambdaClient.LambdaFunctionConfig
import net.playq.tk.aws.lambda.LambdaClientTest.Ctx
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import net.playq.tk.util.{ClosableFile, process}
import software.amazon.awssdk.services.lambda.model.Runtime
import zio.IO

final class LambdaClientTest extends LambdaClientTestBase[IO] with WithProduction

object LambdaClientTest {
  final case class Ctx[F[+_, +_]](
    lambdaFactory: LambdaClientFactory[F],
    log: LogIO2[F],
  )
}

abstract class LambdaClientTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  "Lambda client" should {
    "Deploy, invoke, delete function" skip bound {
      ctx =>
        import ctx.*
        val config = LambdaFunctionConfig("testFunction", "MyFunction::MyFunction.Function::FunctionHandler", Runtime.DOTNETCORE3_1, 128)
        val lambda = lambdaFactory.serviceBased(ServiceName("test"))
        (for {
          dir     <- ClosableFile.resourceTempDirectory()
          _       <- Lifecycle.liftF(F.sync(process.exec(s"dotnet new lambda.EmptyFunction --name MyFunction --output ${dir.absolutePath}")))
          testZip <- lambda.createCSharpDeploymentPackage(dir.file.toPath.resolve("src/MyFunction").toFile)
        } yield testZip).use {
          testZip =>
            for {
              _   <- lambda.createPublish(testZip.file, config)
              res <- lambda.invoke(config.name, "\"someUrl\"")
              _   <- log.info(s"$res")
              _   <- lambda.delete(config.name)
            } yield ()
        }
    }
  }
}
