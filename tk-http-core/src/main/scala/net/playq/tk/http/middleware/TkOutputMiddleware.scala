package net.playq.tk.http.middleware

import izumi.idealingua.runtime.rpc.IRTWrappedService

trait TkOutputMiddleware[F[+_, +_]] {
  def modifyOutput[C](service: IRTWrappedService[F, C]): IRTWrappedService[F, C]
}

object TkOutputMiddleware {
  final class Empty[F[+_, +_]] extends TkOutputMiddleware[F] {
    override def modifyOutput[C](service: IRTWrappedService[F, C]): IRTWrappedService[F, C] = service
  }
}
