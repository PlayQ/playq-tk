package net.playq.tk.npmresolver

import izumi.functional.bio.{F, IO2}

import scala.collection.mutable

trait NPMLibsCache[F[_, _]] {
  def put(libDescriptor: String, version: String, content: Array[Byte]): F[Throwable, Unit]
  def fetch(libDescriptor: String, version: String): F[Throwable, Option[Array[Byte]]]
  def remove(libDescriptor: String, version: String): F[Throwable, Unit]
}

object NPMLibsCache {
  final class Dummy[F[+_, +_]: IO2] extends NPMLibsCache[F] {

    private[this] val storage = mutable.HashMap.empty[String, Array[Byte]]

    override def put(libDescriptor: String, version: String, content: Array[Byte]): F[Throwable, Unit] = F.syncThrowable {
      synchronized {
        storage.put(libDescriptor + version, content)
      }
    }.void

    override def fetch(libDescriptor: String, version: String): F[Throwable, Option[Array[Byte]]] = F.syncThrowable {
      synchronized {
        storage.get(libDescriptor + version)
      }
    }

    override def remove(libDescriptor: String, version: String): F[Throwable, Unit] = F.syncThrowable {
      synchronized {
        storage.remove(libDescriptor + version)
      }
    }.void
  }
}
