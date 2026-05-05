package net.playq.tk.http.ws

import izumi.functional.bio.Bifunctor2

sealed abstract class WsInfraException(message: String, cause: Option[Throwable]) extends RuntimeException(message, cause.orNull)

object WsInfraException {
  final implicit class ThrowableWsOps[F[+_, +_], A](f: F[Throwable, A]) {
    def leftMapCodecFailure(implicit bifunctor2: Bifunctor2[F]): F[CodecFailed, A] =
      f.leftMap(e => CodecFailed(e.getMessage, Some(e)))
  }

  final case class HandlerNotFound(tag: String) extends WsInfraException(s"Missing WS handler for $tag", None)
  final case class RequestFailed(message: String) extends WsInfraException(message, None)
  final case class CodecFailed(message: String, cause: Option[Throwable]) extends WsInfraException(message, cause)
  case object ExchangeFailed extends WsInfraException("Response expected but got nothing", None)
}
