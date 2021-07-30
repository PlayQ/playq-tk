package net.playq.tk.aws.cost

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.IO2
import izumi.fundamentals.platform.strings.IzString._
import CostClientTest.Ctx
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import net.playq.tk.test.rnd.TkRndGeneric
import org.scalatest.Assertion
import zio.IO

import java.time.LocalDate

final class CostClientTest extends CostClientTestBase[IO] with WithProduction

object CostClientTest {
  final case class Ctx[F[+_, +_]](
    client: CostClient[F]
  )
}

abstract class CostClientTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] with TkRndGeneric {

  private[this] def skip(): Assertion = {
    val awsCostTestsEnabled = System.getProperty("aws.cost.tests.enable").asBoolean().getOrElse(false)
    assume(awsCostTestsEnabled, "Cost test skipped. To enable test add AWS credentials into your environment.")
  }

  "Cost client" should {
    "Get cost" in scopeIO {
      ctx =>
        import ctx._
        skip()
        val request = CostRequest(
          LocalDate.now().minusDays(7),
          LocalDate.now(),
          Seq(
            "BlendedCost",
            "UnblendedCost",
            "AmortizedCost",
            "NormalizedUsageAmount",
            "UsageQuantity",
          ),
          "DAILY",
        )
//          .withGroupBy(
//          Seq(
//            CostGroupDefinition("DIMENSION", "RESOURCE_ID"),
//            CostGroupDefinition("TAG", "Environment")
//          )
//        )
//          .withFilter(
//            CostExpression()
//              .withDimensions(CostKeyValues("SERVICE", Seq("Amazon Simple Storage Service")))
//          )

        for {
          _ <- client.getCosts(request)
        } yield ()

    }
  }

}
