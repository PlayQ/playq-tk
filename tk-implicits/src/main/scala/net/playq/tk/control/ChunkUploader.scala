package net.playq.tk.control

import cats.effect.{Concurrent, Timer}
import cats.syntax.functor._
import cats.syntax.flatMap._
import fs2.Chunk
import fs2.concurrent.Queue

import scala.concurrent.duration._

final case class ChunkUploader[F[_], A](push: A => F[Unit], kill: F[Unit])

object ChunkUploader {

  final case class ChunkUploaderConfig(chunkSize: Int, pushInterval: FiniteDuration)

  def createNew[F[_]: Concurrent: Timer, Elem](uploadChunk: Chunk[Elem] => F[Unit])(implicit cfg: ChunkUploaderConfig): F[ChunkUploader[F, Elem]] = {
    for {
      q <- Queue.unbounded[F, Elem]
      pusherFiber <- Concurrent[F].start {
        q.dequeue
          .groupWithin(cfg.chunkSize, cfg.pushInterval)
          .evalMap(uploadChunk)
          .compile
          .drain
      }
    } yield ChunkUploader(q.enqueue1, pusherFiber.cancel)
  }
}
