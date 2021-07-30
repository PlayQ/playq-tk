package net.playq.tk.http.middleware

import io.circe.Json
import izumi.idealingua.runtime.rpc.IRTMethodId

trait MuxerMiddleware[F[+_, +_], -C] {
  self =>
  def priority: Int
  def doInvoke(parsedBody: Json, context: C, method: IRTMethodId, next: F[Throwable, Option[Json]]): F[Throwable, Option[Json]]

  final def andThen[C1 <: C](that: MuxerMiddleware[F, C1]): MuxerMiddleware[F, C1] = new MuxerMiddleware[F, C1] {
    override def priority: Int = self.priority
    override def doInvoke(parsedBody: Json, context: C1, method: IRTMethodId, next: F[Throwable, Option[Json]]): F[Throwable, Option[Json]] = {
      val thatRes = that.doInvoke(parsedBody, context, method, next)
      self.doInvoke(parsedBody, context, method, thatRes)
    }
  }
}
