package net.playq.tk.aws.s3

import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{F, IO2}
import net.playq.tk.aws.s3.S3ResourceSkipTest.Ctx
import net.playq.tk.aws.s3.config.S3Config
import net.playq.tk.test.{TkTestBaseCtx, WithProduction}
import zio.IO

final class S3ResourceSkipTest extends S3ResourceSkipTestBase[IO] with WithProduction

object S3ResourceSkipTest {
  final case class Ctx[F[+_, +_]](s3Upload: S3Component[F])
}

abstract class S3ResourceSkipTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    import scala.concurrent.duration.*
    make[S3Config].from(S3Config(Some("http://asda_sadas"), 1000.seconds))
  }

  "should skip (scope)" in bound {
    _ =>
      F.fail("This test should be skipped")
  }

  "should skip" in {
    (_: S3Component[F]) =>
      F.fail("This test should be skipped"): F[String, Unit]
  }
}
