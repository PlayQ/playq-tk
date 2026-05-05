package net.playq.tk.rocksdb

import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.{Async2, F}
import net.playq.tk.rocksdb.RocksDBTest.{Ctx, TestRocksBaseId}
import net.playq.tk.test.{TkTestBaseCtx, WithDummy, WithProduction}
import zio.IO

final class RocksDBTestDummy extends RocksDBTestBase[IO] with WithDummy
final class RocksDBTestProd extends RocksDBTestBase[IO] with WithProduction

object RocksDBTest {
  final class TestRocksBaseId extends RocksBaseId("rocksdbtest/test", true)

  final case class Ctx[F[+_, +_]](
    id: TestRocksBaseId,
    db: RocksBase[F, TestRocksBaseId],
    factory: RocksBaseFactory[F],
  )
}
abstract class RocksDBTestBase[F[+_, +_]: Async2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    include(RocksBase.rocksBaseModule[F, TestRocksBaseId])
  }

  "RocksBase" should {
    "perform base operations" in bound {
      ctx =>
        import ctx.*
        val key   = "testKey"
        val value = "testValue"
        for {
          _   <- db.put(key, value)
          get <- db.get(key)
          _   <- assertIO(get.contains("testValue"))
          _   <- db.delete(key)
          _   <- factory.tmpBase().use {
            db1 =>
              for {
                _    <- db1.put(key, value)
                get1 <- db1.get(key)
                _    <- assertIO(get1.contains("testValue"))
                _    <- db1.delete(key)
              } yield ()
          }
        } yield ()
    }

    "perform parallel close/flushes without locks or segfaults" in bound {
      ctx =>
        import ctx.*
        F.parTraverse(1 to 10) {
          _ =>
            db.flush(force = false).attempt *>
            db.close() *>
            db.flush(force = true).attempt
        }
    }
  }
}
