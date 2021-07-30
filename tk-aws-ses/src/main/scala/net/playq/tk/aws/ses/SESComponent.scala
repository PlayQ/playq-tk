package net.playq.tk.aws.ses

import izumi.functional.bio.BlockingIO2
import software.amazon.awssdk.services.ses.SesClient

final class SESComponent[F[+_, +_]](sesClient: SesClient)(implicit F: BlockingIO2[F]) {
  def makeRequest[A](f: SesClient => A): F[Throwable, A] = {
    F.syncBlocking(f(sesClient))
  }
}
