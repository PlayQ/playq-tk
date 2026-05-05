package net.playq.tk.control

import cats.effect.std.Queue
import cats.effect.{Concurrent, Temporal}
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import fs2.{Chunk, Stream}

import scala.concurrent.duration.*

final case class ChunkUploader[F[_], A](push: A => F[Unit], kill: F[Unit])

object ChunkUploader {

  final case class ChunkUploaderConfig(chunkSize: Int, pushInterval: FiniteDuration)

  def createNew[F[_]: Temporal, Elem](uploadChunk: Chunk[Elem] => F[Unit])(implicit cfg: ChunkUploaderConfig): F[ChunkUploader[F, Elem]] = {
    for {
      q           <- Queue.unbounded[F, Elem]
      pusherFiber <- Concurrent[F].start {
        Stream
          .fromQueueUnterminated(q)
          .groupWithin(cfg.chunkSize, cfg.pushInterval)
          .evalMap(uploadChunk)
          .compile
          .drain
      }
    } yield ChunkUploader(q.offer, pusherFiber.cancel)
  }
}
