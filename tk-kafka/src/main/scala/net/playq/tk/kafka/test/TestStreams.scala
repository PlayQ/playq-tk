package net.playq.tk.kafka.test

import cats.effect.Blocker
import cats.syntax.applicative.*
import cats.syntax.functor.*
import fs2.Stream
import io.circe.{Decoder, parser}
import net.playq.tk.fs2kafka.KafkaData
import izumi.fundamentals.platform.resources.IzResources.*
import net.playq.tk.quantified.{ContextShiftThrowable, SyncThrowable}

import java.io.InputStream
import scala.util.chaining.*

trait TestStreams[F[_, _]] {
  def resourceKafkaStream[A: Decoder](file: String): F[Throwable, Stream[F[Throwable, _], KafkaData[A]]]
  def resourceJsonStream[A: Decoder](file: String): F[Throwable, Stream[F[Throwable, _], A]]
}

object TestStreams {
  final class Impl[F[_, _]](
    implicit
    F: SyncThrowable[F],
    C: ContextShiftThrowable[F],
  ) extends TestStreams[F] {

    @SuppressWarnings(Array("OptionGet"))
    def resourceKafkaStream[A: Decoder](file: String): F[Throwable, Stream[F[Throwable, _], KafkaData[A]]] =
      F.delay(getClass.read(file).get)
        .map(kafkaStream[A])

    @SuppressWarnings(Array("OptionGet"))
    def resourceJsonStream[A: Decoder](file: String): F[Throwable, Stream[F[Throwable, _], A]] =
      F.delay(getClass.read(file).get)
        .map(jsonStream[A])

    private[this] def kafkaStream[A: Decoder](inputStream: InputStream): Stream[F[Throwable, _], KafkaData[A]] =
      jsonStream(inputStream).zip(Stream.iterate(0L)(_ + 1L)).map { case (ev, i) => KafkaData(topic = "dummy-topic", partition = 0, offset = i, value = ev) }

    private[this] def jsonStream[A: Decoder](inputStream: InputStream): Stream[F[Throwable, _], A] = {
      val globalPool = Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.Implicits.global)
      fs2.io
        .readInputStream[F[Throwable, _]](inputStream.pure, 64 * 1024, globalPool)
        .pipe(fs2.text.utf8Decode)
        .pipe(fs2.text.lines)
        .filter(_.nonEmpty)
        .evalMap[F[Throwable, _], A] {
          json =>
            parser
              .parse(json).flatMap(_.as[A]).fold(
                e => F.raiseError(new RuntimeException(s"Couldn't parse $json because ${e.getMessage}", e)),
                _.pure,
              )
        }
    }
  }
}
