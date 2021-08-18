package net.playq.tk.rocksdb

import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.IO2
import net.playq.tk.rocksdb.RocksDBTest.{Ctx, TestRocksBaseId}
import net.playq.tk.test.{TkTestBaseCtx, WithDummy, WithProduction}
import zio.IO

final class RocksDBTestDummy extends RocksDBTestBase[IO] with WithDummy
final class RocksDBTestProd extends RocksDBTestBase[IO] with WithProduction

object RocksDBTest {
  final class TestRocksBaseId extends RocksBaseId("/tmp/rocksdbtest", "test")

  final case class Ctx[F[+_, +_]](
    db: RocksBase[F, TestRocksBaseId]
  )
}
abstract class RocksDBTestBase[F[+_, +_]: IO2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    include(RocksBase.rocksBaseModule[F, TestRocksBaseId])
  }

  "perform base operations" in scopeIO {
    ctx =>
      import ctx._
      val key   = "testKey"
      val value = "testValue"
      for {
        _   <- db.put(key, value)
        get <- db.get(key)
        _   <- assertIO(get sameElements "testValue")
        _   <- db.delete(key)
      } yield ()
  }
}
