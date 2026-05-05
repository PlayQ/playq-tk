package net.playq.tk.redis.aws

import izumi.functional.bio.Clock1
import izumi.fundamentals.platform.functional.Identity
import net.playq.tk.redis.aws.ElastiCacheIamCredentialsProvider.{EXPIRATION_DURATION, TimedCredentials}
import net.playq.tk.redis.config.RedisConfig.RedisAuth
import org.redisson.config.{Credentials, CredentialsResolver}
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

import java.net.InetSocketAddress
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CompletableFuture, CompletionStage}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final case class ElastiCacheIamCredentialsProvider(
  auth: RedisAuth.AwsIAM,
  clock: Clock1[Identity],
) extends CredentialsResolver {
  private val awsCredentialsProvider = DefaultCredentialsProvider.builder().build()
  private val signer                 = ElastiCacheIamSigner(auth, clock)

  private val buffer: AtomicReference[TimedCredentials] = new AtomicReference(null)

  override def resolve(address: InetSocketAddress): CompletionStage[Credentials] = {
    buffer.updateAndGet {
      case null                                                                  => createCredentials()
      case TimedCredentials(_, expireAt) if expireAt.isBefore(clock.nowOffset()) => createCredentials()
      case tc                                                                    => tc
    }.future
  }

  private def createCredentials(): TimedCredentials = {
    val token = signer.signedConnectRequest(awsCredentialsProvider.resolveCredentials(), EXPIRATION_DURATION)
    val creds = new Credentials(auth.awsUserId, token)
    TimedCredentials(creds, clock.nowOffset().plusSeconds(EXPIRATION_DURATION.toSeconds))
  }
}

object ElastiCacheIamCredentialsProvider {
  private val EXPIRATION_DURATION: FiniteDuration = 15.minutes
  final case class TimedCredentials(credentials: Credentials, expireAt: OffsetDateTime) {
    val future: CompletableFuture[Credentials] = CompletableFuture.completedFuture(credentials)
  }
}
