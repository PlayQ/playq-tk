package net.playq.tk.aws.s3.config

import net.playq.tk.aws.s3.config.S3RetryConfig.Strategy
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy
import software.amazon.awssdk.retries.api.{BackoffStrategy, RetryStrategy}
import software.amazon.awssdk.retries.{AdaptiveRetryStrategy, LegacyRetryStrategy, StandardRetryStrategy}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.DurationConverters.ScalaDurationOps

final case class S3RetryConfig(
  strategy: S3RetryConfig.Strategy,
  requestAttempts: Int,
  minBackoff: FiniteDuration,
  maxBackoff: FiniteDuration,
  minThrottlingBackoff: FiniteDuration,
  maxThrottlingBackoff: FiniteDuration,
) {
  def retryStrategy: RetryStrategy = strategy match {
    case Strategy.Adaptive => createAdaptiveRetryStrategy()
    case Strategy.Standard => createStandardRetryStrategy()
    case Strategy.Legacy   => createLegacyRetryStrategy()
  }

  private def createAdaptiveRetryStrategy(): AdaptiveRetryStrategy = {
    AwsRetryStrategy
      .adaptiveRetryStrategy().toBuilder
      .maxAttempts(requestAttempts)
      .backoffStrategy(backoffStrategy)
      .throttlingBackoffStrategy(throttlingBackoffStrategy)
      .build()
  }

  private def createStandardRetryStrategy(): StandardRetryStrategy = {
    AwsRetryStrategy
      .standardRetryStrategy().toBuilder
      .maxAttempts(requestAttempts)
      .backoffStrategy(backoffStrategy)
      .throttlingBackoffStrategy(throttlingBackoffStrategy)
      .build()
  }

  private def createLegacyRetryStrategy(): LegacyRetryStrategy = {
    AwsRetryStrategy
      .legacyRetryStrategy().toBuilder
      .maxAttempts(requestAttempts)
      .backoffStrategy(backoffStrategy)
      .throttlingBackoffStrategy(throttlingBackoffStrategy)
      .build()
  }

  private def backoffStrategy: BackoffStrategy = {
    BackoffStrategy.exponentialDelay(minBackoff.toJava, maxBackoff.toJava)
  }

  private def throttlingBackoffStrategy: BackoffStrategy = {
    BackoffStrategy.exponentialDelay(minThrottlingBackoff.toJava, maxThrottlingBackoff.toJava)
  }
}

object S3RetryConfig {
  sealed trait Strategy
  object Strategy {
    case object Adaptive extends Strategy
    case object Standard extends Strategy
    case object Legacy extends Strategy
  }

  /** Standard strategy with 3 attempts and default backoff.
    * Recommended for general purposes.
    */
  def default: S3RetryConfig = S3RetryConfig(
    strategy             = Strategy.Standard,
    requestAttempts      = 3,
    minBackoff           = 100.millis,
    maxBackoff           = 5.second,
    minThrottlingBackoff = 1.second,
    maxThrottlingBackoff = 20.second,
  )

  /** Standard strategy with 5 attempts and default backoff.
    * Recommended for better success rates during high load.
    */
  def balanced: S3RetryConfig = S3RetryConfig(
    strategy             = Strategy.Standard,
    requestAttempts      = 5,
    minBackoff           = 100.millis,
    maxBackoff           = 5.seconds,
    minThrottlingBackoff = 1.seconds,
    maxThrottlingBackoff = 20.seconds,
  )

  /** 10 attempts with total of 111 seconds of await for throttling requests.
    * Recommended for places where latency is not essential and success rate is critical.
    */
  def maxSuccessHighLatency: S3RetryConfig = S3RetryConfig(
    strategy             = Strategy.Adaptive,
    requestAttempts      = 10,
    minBackoff           = 100.millis,
    maxBackoff           = 1.second,
    minThrottlingBackoff = 1.second,
    maxThrottlingBackoff = 20.second,
  )
}
