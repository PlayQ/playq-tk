package net.playq.tk.http.auth

import io.circe.{DecodingFailure, Json}
import izumi.functional.bio.IO2
import izumi.idealingua.runtime.rpc.{IRTCirceMarshaller, IRTJsonBody, IRTMethodId, IRTMethodWrapper, IRTReqBody, IRTResBody, IRTServiceId, IRTWrappedService}

abstract class AuthWrapper[F[_, _], C](
  wrapped: IRTWrappedService[F, C]
) extends IRTMappedService[F, C](wrapped)

sealed abstract class IRTMappedService[F[_, _], C](
  wrapped: IRTWrappedService[F, C]
) extends IRTWrappedService[F, C] {
  self =>
  override final def serviceId: IRTServiceId                                   = wrapped.serviceId
  override final lazy val allMethods: Map[IRTMethodId, IRTMethodWrapper[F, C]] = wrapped.allMethods.view.mapValues(wrapMethod).toMap
  private[this] def wrapMethod(method: IRTMethodWrapper[F, C]): IRTMethodWrapper[F, C] = {
    new IRTMethodWrapper[F, C] {
      override val signature: method.signature.type   = method.signature
      override val marshaller: method.marshaller.type = method.marshaller
      override def invoke(ctx: C, input: signature.Input): F[Nothing, signature.Output] =
        self.invoke(method: method.type)(ctx, input)
    }
  }

  def invoke(method: IRTMethodWrapper[F, C])(ctx: C, input: method.signature.Input): F[Nothing, method.signature.Output]
}

abstract class OutputWrapper[F[_, _], C](
  wrapped: IRTWrappedService[F, C]
) extends IRTWrappedService[F, C] {
  self =>
  override final def serviceId: IRTServiceId                                   = wrapped.serviceId
  override final lazy val allMethods: Map[IRTMethodId, IRTMethodWrapper[F, C]] = wrapped.allMethods.view.mapValues(wrapMethod).toMap
  private[this] def wrapMethod(method: IRTMethodWrapper[F, C]): IRTMethodWrapper[F, C] = {
    new IRTMethodWrapper[F, C] {
      override val signature: method.signature.type = method.signature
      override val marshaller: IRTCirceMarshaller = new IRTCirceMarshaller {
        override def encodeRequest: PartialFunction[IRTReqBody, Json]  = method.marshaller.encodeRequest
        override val encodeResponse: PartialFunction[IRTResBody, Json] = encode(method)
        override def decodeRequest[Or[+_, +_]: IO2]: PartialFunction[IRTJsonBody, Or[DecodingFailure, IRTReqBody]] =
          method.marshaller.decodeRequest
        override def decodeResponse[Or[+_, +_]: IO2]: PartialFunction[IRTJsonBody, Or[DecodingFailure, IRTResBody]] =
          method.marshaller.decodeResponse
      }
      override def invoke(ctx: C, input: signature.Input): F[Nothing, signature.Output] = method.invoke(ctx, input)
    }
  }

  def encode(method: IRTMethodWrapper[F, C]): PartialFunction[IRTResBody, Json]
}
