package net.playq.tk.npmresolver

import distage.{Module, ModuleDef, TagKK}
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.Async2
import net.playq.tk.npmresolver.NPMResolverTest.Ctx
import net.playq.tk.test.{TkTestBaseCtx, WithDummy}
import net.playq.tk.util.ClosableFile
import zio.IO

final class NPMResolverTestDummy extends NPMResolverTestBase[IO] with WithDummy

object NPMResolverTest {
  final case class Ctx[F[+_, +_]](
    resolver: NPMResolver[F]
  )
}
abstract class NPMResolverTestBase[F[+_, +_]: Async2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] with WithDummy {

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    make[NPMResolver[F]]
  }

  "NPMResolver" should {
    "resolve npm dependencies" in bound {
      ctx =>
        import ctx.*
        val artifact = NPMArtifact("romanize", "1.1.1")

        ClosableFile.resourceTempDirectory("npmresolver").map(_.absolutePath).use {
          path =>
            for {
              _ <- resolver.fetchArtifact(path, artifact)

              // from cache
              _ <- resolver.fetchArtifact(path, artifact)
            } yield ()
        }
    }
  }
}
