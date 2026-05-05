package d4s.services

import d4s.config.DynamoConfig
import distage.Lifecycle
import izumi.distage.model.definition.Id
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.functional.bio.{F, IO2}
import izumi.fundamentals.platform.integration.{PortCheck, ResourceCheck}
import net.playq.tk.aws.http.TkAwsHttpClient
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.client.config.{ClientAsyncConfiguration, ClientOverrideConfiguration, SdkAdvancedAsyncClientOption}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbAsyncClientBuilder}

import java.net.URI
import java.util.concurrent.ThreadPoolExecutor
import scala.util.chaining.*

final case class DynamoComponent(client: DynamoDbAsyncClient)

object DynamoComponent {

  final class Resource[F[+_, +_]: IO2](
    conf: DynamoConfig,
    httpClient: TkAwsHttpClient,
    blockingExecutor: ThreadPoolExecutor @Id("blockingIO"),
    portCheck: PortCheck @Id("dynamo-port"),
  ) extends Lifecycle.Basic[F[Throwable, _], DynamoComponent]
    with IntegrationCheck[F[Throwable, _]] {

    override def resourcesAvailable(): F[Throwable, ResourceCheck] = F.sync {
      conf.maybeLocalUrl.fold(ResourceCheck.Success(): ResourceCheck) {
        url =>
          portCheck.checkUrl(new URI(url).toURL, "DynamoClient")
      }
    }

    override def acquire: F[Throwable, DynamoComponent] = F.syncThrowable {
      DynamoComponent(mkClient())
    }

    override def release(resource: DynamoComponent): F[Throwable, Unit] = F.syncThrowable {
      resource.client.close()
    }

    private[this] def mkClient(): DynamoDbAsyncClient = {
      DynamoDbAsyncClient
        .builder()
        .httpClient(httpClient.client)
        .pipe(setTimeout)
        .pipe(setRegion)
        .pipe(setEndpoint)
        .pipe(setAsyncConfig)
        .build()
    }

    private[this] def setAsyncConfig(builder: DynamoDbAsyncClientBuilder): DynamoDbAsyncClientBuilder = {
      val asyncConfig = ClientAsyncConfiguration
        .builder()
        .advancedOption(SdkAdvancedAsyncClientOption.FUTURE_COMPLETION_EXECUTOR, blockingExecutor)
        .build()
      builder.asyncConfiguration(asyncConfig)
    }

    private[this] def setTimeout(builder: DynamoDbAsyncClientBuilder): DynamoDbAsyncClientBuilder = {
      builder.overrideConfiguration(
        ClientOverrideConfiguration
          .builder()
          .apiCallAttemptTimeout(java.time.Duration.ofMillis(conf.connectionTimeout.toMillis))
          .build()
      )
    }

    private[this] def setEndpoint(builder: DynamoDbAsyncClientBuilder): DynamoDbAsyncClientBuilder = {
      conf.maybeLocalUrl.fold(builder) {
        url =>
          builder
            .endpointOverride(URI.create(url))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
      }
    }

    private[this] def setRegion(builder: DynamoDbAsyncClientBuilder): DynamoDbAsyncClientBuilder = {
      conf.getRegion.fold(builder)(region => builder.region(Region.of(region)))
    }
  }

}
