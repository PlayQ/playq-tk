package net.playq.tk.http.middleware

import io.circe.Json
import izumi.functional.bio.IO2
import izumi.idealingua.runtime.rpc.{ContextExtender, IRTMethodId, IRTServerMultiplexorImpl, IRTWrappedService}

final class IRTMultiplexorWithMiddleware[F[+_, +_]: IO2, BasicRequestContext, RequestContext](
  services: Set[IRTWrappedService[F, RequestContext]],
  extender: ContextExtender[BasicRequestContext, RequestContext],
  middlewares: Set[MuxerMiddleware[F, BasicRequestContext]],
) extends IRTServerMultiplexorImpl[F, BasicRequestContext, RequestContext](services, extender) {

  override def doInvoke(parsedBody: Json, context: BasicRequestContext, toInvoke: IRTMethodId): F[Throwable, Option[Json]] = {
    composedMiddleware match {
      case Some(middleware) => middleware.doInvoke(parsedBody, context, toInvoke, super.doInvoke(parsedBody, context, toInvoke))
      case None             => super.doInvoke(parsedBody, context, toInvoke)
    }
  }

  private[this] val composedMiddleware: Option[MuxerMiddleware[F, BasicRequestContext]] = {
    middlewares.toList.sortBy(_.priority).reduceOption(_.andThen(_))
  }
}
