package net.playq.tk.rocksdb

import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import net.playq.tk.rocksdb.RocksDBTest.{Ctx, TestRocksBaseId}
import net.playq.tk.rocksdb.test.RocksDBTestEnv
import net.playq.tk.test.{TkTestBase, WithDummy, WithProduction}
import zio.IO

final class RocksDBTestDummy extends RocksDBTestBase[IO] with WithDummy
final class RocksDBTestProd extends RocksDBTestBase[IO] with WithProduction

object RocksDBTest {
  class TestRocksBaseId extends RocksBaseId[TestRocksBaseId]("/tmp/rocksdbtest", "test"){
    override def updatePath(newBase: String, newDpb: String): TestRocksBaseId = new TestRocksBaseId {
      override val base = newBase
      override val dpb = newDpb
    }
  }

  final case class Ctx(
    db: RocksBase[IO, TestRocksBaseId]
  )
}
abstract class RocksDBTestBase[F[+_, +_]: TagKK] extends TkTestBase[Ctx] with RocksDBTestEnv[IO] {
  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    include(RocksBase.rocksBaseModule[F, TestRocksBaseId])
  }

  "perform base operations" in scopeIO {
    ctx =>
      import ctx.*
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
