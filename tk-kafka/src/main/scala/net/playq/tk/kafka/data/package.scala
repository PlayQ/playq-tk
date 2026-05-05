package net.playq.tk.kafka

import fs2.{Pipe, Stream}

/** yet another fs2-kafka adapter */
package object data {
  final type Stream2[+F[_, _], +A]  = Stream[F[Throwable, _], A]
  final type Pipe2[F[_, _], -A, +B] = Pipe[F[Throwable, _], A, B]

  final type Partition    = Int
  final type Offset       = Long
  final type CommitOffset = Long
}
