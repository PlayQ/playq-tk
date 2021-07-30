package net.playq.tk.cache

import distage.TagKK
import distage.config.ConfigModuleDef
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.IO2
import CacheTest.Ctx
import SimpleCacheWithAssertions._
import net.playq.tk.test.{TkTestBaseCtx, WithDummy}
import zio.IO

final class CacheTest extends CacheTestBase[IO] with WithDummy

object CacheTest {
  final case class Ctx[F[+_, +_]](
    cache: SimpleCacheWithAssertions[F]
  )
}

abstract class CacheTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {

  "Cache" should {
    "perform base crud" in scopeIO {
      ctx =>
        import ctx._

        for {

          _ <- cache.get("1").flatMap(s => assertIO(s == genValue("1")))
          _ <- cache.get("1").flatMap(s => assertIO(s == genValue("1")))
          _ <- cache.get("3").flatMap(s => assertIO(s == genValue("3")))
          _ <- cache.get("3").flatMap(s => assertIO(s == genValue("3")))

          toUpdate  = "2"
          vToUpdate = genValue(toUpdate)

          _ <- cache.reloadAll(Set(toUpdate))

          _ <- assertIO(state.contains(toUpdate))
          _ <- cache.get(toUpdate).flatMap(s => assertIO(s == vToUpdate))
        } yield ()
    }
  }

  override def moduleOverrides: Module = super.moduleOverrides ++ new ConfigModuleDef {
    make[SimpleCacheWithAssertions[F]]
  }
}
