package net.playq.tk.aws.cost

import distage.TagKK
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{Clock1, IO2}
import izumi.fundamentals.platform.strings.IzString.*
import net.playq.tk.aws.cost.CostClientTest.Ctx
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import org.scalatest.Assertion
import zio.IO

final class CostClientTest extends CostClientTestBase[IO] with WithProduction

object CostClientTest {
  final case class Ctx[F[+_, +_]](
    client: CostClient[F]
  )
}

abstract class CostClientTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {

  private[this] def skip(): Assertion = {
    val awsCostTestsEnabled = System.getProperty("aws.cost.tests.enable").asBoolean().getOrElse(false)
    assume(awsCostTestsEnabled, "Cost test skipped. To enable test add AWS credentials into your environment.")
  }

  "Cost client" should {
    "Get cost" in bound {
      ctx =>
        import ctx.*
        skip()
        val request = CostRequest(
          Clock1.Standard.nowZoned().toLocalDate.minusDays(7),
          Clock1.Standard.nowZoned().toLocalDate,
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
