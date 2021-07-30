package net.playq.tk.http.middleware

import izumi.idealingua.runtime.rpc.IRTWrappedService

trait TGOutputMiddleware[F[+_, +_]] {
  def modifyOutput[C](service: IRTWrappedService[F, C]): IRTWrappedService[F, C]
}

object TGOutputMiddleware {
  final class Empty[F[+_, +_]] extends TGOutputMiddleware[F] {
    override def modifyOutput[C](service: IRTWrappedService[F, C]): IRTWrappedService[F, C] = service
  }
}
