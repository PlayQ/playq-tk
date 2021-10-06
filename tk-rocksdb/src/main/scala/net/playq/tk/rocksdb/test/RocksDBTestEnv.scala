package net.playq.tk.rocksdb.test

import distage.{DIKey, ModuleDef, Scene, TagKK}
import izumi.distage.model.definition.Lifecycle
import izumi.distage.testkit.TestConfig
import izumi.functional.bio.{F, IO2}
import net.playq.tk.rocksdb.test.RocksDBTestEnv.RocksDBCleanup
import net.playq.tk.rocksdb.{RocksBaseId, RocksDBConfig}
import net.playq.tk.test.{ForcedRoots, MemoizationRoots, ModuleOverrides, WithProduction}

import java.io.File
import scala.reflect.io.Directory

trait RocksDBTestEnv[F[+_, +_]] extends MemoizationRoots with ModuleOverrides with ForcedRoots with WithProduction {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: TestConfig.PriorAxisDIKeys = {
    super.memoizationRoots ++
      Map(0 -> Map(Scene.Managed -> DIKey[RocksDBConfig])) +
      (1 -> DIKey[RocksDBCleanup[F]])
  }

  abstract override def moduleOverrides: distage.Module = super.moduleOverrides ++ new ModuleDef {
    make[RocksDBCleanup[F]].fromResource[RocksDBCleanup[F]]
  }

  abstract override def forcedRoots: TestConfig.AxisDIKeys = {
    super.forcedRoots ++ Set(DIKey[RocksDBCleanup[F]])
  }
}

object RocksDBTestEnv {
  final class RocksDBCleanup[F[+_, +_]: IO2](
    bases: Set[RocksBaseId[?]],
  ) extends Lifecycle.Self[F[Throwable, _], RocksDBCleanup[F]] {
    override def acquire: F[Throwable, Unit] = F.unit
    override def release: F[Throwable, Unit] = {
      F.traverse_(bases){
        id =>
          val directory = new Directory(new File(id.base))
          F.syncThrowable(directory.deleteRecursively()).void
      }
    }
  }
}
