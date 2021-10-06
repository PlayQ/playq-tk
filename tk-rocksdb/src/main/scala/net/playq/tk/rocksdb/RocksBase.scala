package net.playq.tk.rocksdb

import distage.{ModuleDef, Tag, TagKK}
import izumi.distage.constructors.ClassConstructor
import izumi.distage.model.definition.StandardAxis.Mode
import izumi.functional.bio.{F, IO2}

import scala.collection.mutable

trait RocksBase[F[_, _], RocksDBId] {
  def put[K: RocksDBEncoder, V: RocksDBEncoder](key: K, value: V): F[Throwable, Unit]
  def get[K: RocksDBEncoder](key: K): F[Throwable, Array[Byte]]
  def delete[K: RocksDBEncoder](key: K): F[Throwable, Unit]
}

object RocksBase {
  def rocksBaseModule[F[+_, +_]: TagKK, RocksDBId <: RocksBaseId[RocksDBId]: Tag: ClassConstructor]: ModuleDef = new ModuleDef {
    make[RocksDBId].tagged(Mode.Prod)
    make[RocksDBId].modify(_.updatePath(
      scala.util.Random.alphanumeric.take(5).mkString,
      scala.util.Random.alphanumeric.take(5).mkString)
    ).tagged(Mode.Test)
    make[RocksBase[F, RocksDBId]].fromResource((_: RocksBaseFactory[F]).mkBase(_: RocksDBId))
    many[RocksBaseId[?]]
      .weak[RocksDBId]
  }

  final class Dummy[F[+_, +_]: IO2, RocksDBId] extends RocksBase[F, RocksDBId] {
    private[this] val content = mutable.Map.empty[String, Array[Byte]]
    override def put[K: RocksDBEncoder, V: RocksDBEncoder](key: K, value: V): F[Throwable, Unit] = {
      F.sync {
        content.addOne(RocksDBEncoder.toBytes(key).mkString -> RocksDBEncoder.toBytes(value))
      }.void
    }

    override def get[K: RocksDBEncoder](key: K): F[Throwable, Array[Byte]] = {
      F.sync {
        content.getOrElse(RocksDBEncoder.toBytes(key).mkString, Array.empty)
      }
    }

    override def delete[K: RocksDBEncoder](key: K): F[Throwable, Unit] = {
      F.sync {
        content.remove(RocksDBEncoder.toBytes(key).mkString)
      }.void
    }
  }
}
