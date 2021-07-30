package net.playq.tk.aws.ses

import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BlockingIO2, F, IO2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import net.playq.tk.aws.config.LocalTestCredentials
import net.playq.tk.aws.ses.config.SESConfig
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.VerifyDomainIdentityRequest

import java.net.URI
import scala.util.chaining._

final class SESResource[F[+_, +_]: IO2: BlockingIO2](
  sesConfig: SESConfig,
  portCheck: PortCheck,
  localCredentials: LocalTestCredentials,
) extends Lifecycle.OfInner[F[Throwable, ?], SESComponent[F]]
  with IntegrationCheck[F[Throwable, ?]] {

  override val lifecycle: Lifecycle[F[Throwable, ?], SESComponent[F]] = {
    makeClient.evalTap(verifyDomain).map(new SESComponent(_))
  }

  override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
    sesConfig.getEndpoint.fold(
      ResourceCheck.Success(): ResourceCheck
    )(url => portCheck.checkUrl(URI.create(url).toURL, "SESClient"))
  }

  private[this] def makeClient: Lifecycle[F[Throwable, ?], SesClient] = Lifecycle.fromAutoCloseable(F.syncThrowable {
    SesClient.builder
      .pipe(builder => sesConfig.getRegion.fold(builder)(builder region Region.of(_)))
      .pipe(builder => sesConfig.getEndpoint.fold(builder)(builder endpointOverride URI.create(_) credentialsProvider localCredentials.get))
      .build()
  })

  private[this] def verifyDomain(sesClient: SesClient): F[Throwable, Unit] = {
    F.when(sesConfig.isTestEnv) {
      sesConfig.senderEmail.split("@").toList match {
        case _ :: domain :: Nil =>
          F.syncThrowable(sesClient.verifyDomainIdentity(VerifyDomainIdentityRequest.builder.domain(domain).build)).void
        case _ =>
          F.fail(new RuntimeException("Invalid sender address."))
      }
    }
  }

}
