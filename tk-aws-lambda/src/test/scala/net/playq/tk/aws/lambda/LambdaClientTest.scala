package net.playq.tk.aws.lambda

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.IO2
import net.playq.tk.aws.common.ServiceName
import LambdaClient.LambdaFunctionConfig
import LambdaClientTest.Ctx
import net.playq.tk.util.ManagedFile
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import software.amazon.awssdk.services.lambda.model.Runtime
import zio.IO

final class LambdaClientTest extends LambdaClientTestBase[IO] with WithProduction

object LambdaClientTest {
  final case class Ctx[F[+_, +_]](
    lambdaFactory: LambdaClientFactory[F]
  )
}

abstract class LambdaClientTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  "Lambda client" should {
    "Deploy, invoke, delete function" skip scopeIO {
      ctx =>
        import ctx._
        val testPack = ManagedFile.external("/home/???")
        val config   = LambdaFunctionConfig("testFunction", "MyFunction::MyFunction.Function::FunctionHandler", Runtime.DOTNETCORE3_1, 128)
        val lambda   = lambdaFactory.serviceBased(ServiceName("test"))
        lambda.createCSharpDeploymentPackage(testPack).use {
          testZip =>
            for {
              _   <- lambda.createPublish(testZip, config)
              res <- lambda.invoke(config.name, "\"someUrl\"")
              _    = println(res)
              _   <- lambda.delete(config.name)
            } yield ()
        }
    }
  }

}
