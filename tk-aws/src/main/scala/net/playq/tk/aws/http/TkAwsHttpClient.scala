package net.playq.tk.aws.http

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2}
import net.playq.tk.concurrent.AvailableProcessors
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.{NettyNioAsyncHttpClient, SdkEventLoopGroup}

import java.time.Duration
import java.util.concurrent.ThreadFactory

final case class TkAwsHttpClient(client: SdkAsyncHttpClient)
object TkAwsHttpClient {
  final class Netty[F[+_, +_]: IO2](
    threadFactory: ThreadFactory,
    maxConcurrency: Int           = 1000, // default 50 (trying to execute maximum requests concurrently on a given threadpool)
    maxPending: Int               = 10000, // default 10000
    pendingTimeoutMs: Long        = 5000L, // default 10s
    connectionTimeoutMs: Long     = 2000L, // default 2s
    tlsNegotiationTimeoutMs: Long = 3500L, // default 5s
    idleTimeoutMs: Long           = 20000L, // default 60s
    // seems like short read/write timeouts might cause
    // software.amazon.awssdk.core.exception.SdkClientException: Unable to execute HTTP request: null
    // attempting to completely disable them
    readTimoutMs: Long  = 0L, // default 30s
    writeTimoutMs: Long = 0L, // default 30s
  ) extends Lifecycle[F[Throwable, _], TkAwsHttpClient] {
    override type InnerResource = SdkAsyncHttpClient

    override def acquire: F[Throwable, SdkAsyncHttpClient] = {
      for {
        eventLoopGroupBuilder <- F.sync {
          SdkEventLoopGroup
            .builder()
            .threadFactory(threadFactory)
            .numberOfThreads(AvailableProcessors * 2)
        }
        netty <- F.sync {
          NettyNioAsyncHttpClient
            .builder()
            .eventLoopGroupBuilder(eventLoopGroupBuilder)
            .maxConcurrency(maxConcurrency)
            .maxPendingConnectionAcquires(maxPending)
            .tcpKeepAlive(true)
            .useIdleConnectionReaper(true)
            .connectionTimeToLive(Duration.ZERO)
            .connectionTimeout(Duration.ofMillis(connectionTimeoutMs))
            .tlsNegotiationTimeout(Duration.ofMillis(tlsNegotiationTimeoutMs))
            .connectionAcquisitionTimeout(Duration.ofMillis(pendingTimeoutMs))
            .connectionMaxIdleTime(Duration.ofMillis(idleTimeoutMs))
            .readTimeout(if (readTimoutMs <= 0L) Duration.ZERO else Duration.ofMillis(readTimoutMs))
            .writeTimeout(if (writeTimoutMs <= 0L) Duration.ZERO else Duration.ofMillis(writeTimoutMs))
            // stated to have better performance
            // unfortunately it's not available for ARM based MacOS
            // .sslProvider(SslProvider.OPENSSL)
            .build()
        }
      } yield netty
    }

    override def release(resource: SdkAsyncHttpClient): F[Throwable, Unit] = F.sync(resource.close())

    override def extract[B >: TkAwsHttpClient](resource: SdkAsyncHttpClient): Either[F[Throwable, B], B] = {
      Right(TkAwsHttpClient(resource))
    }
  }
}
