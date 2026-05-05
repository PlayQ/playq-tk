package net.playq.tk.redis.aws

import izumi.functional.bio.Clock1
import izumi.fundamentals.platform.functional.Identity
import net.playq.tk.redis.config.RedisConfig.RedisAuth
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner.AuthLocation
import software.amazon.awssdk.http.auth.aws.signer.{AwsV4FamilyHttpSigner, AwsV4HttpSigner}
import software.amazon.awssdk.http.auth.spi.signer.SignRequest
import software.amazon.awssdk.http.{SdkHttpFullRequest, SdkHttpMethod, SdkHttpRequest}
import software.amazon.awssdk.regions.Region

import java.net.URI
import java.util.Collections
import scala.concurrent.duration.FiniteDuration
import scala.jdk.DurationConverters.ScalaDurationOps

final case class ElastiCacheIamSigner(
  auth: RedisAuth.AwsIAM,
  clock: Clock1[Identity],
) {
  def signedConnectRequest(credentials: AwsCredentials, expiration: FiniteDuration): String = {
    val connectionRequest       = createConnectionRequest
    val signedConnectionRequest = sign(connectionRequest, credentials, expiration)
    // render as uri and remove protocol part
    signedConnectionRequest.getUri.toString.replace(ElastiCacheIamSigner.REQUEST_PROTOCOL, "")
  }

  private def sign(request: SdkHttpRequest, credentials: AwsCredentials, expiration: FiniteDuration): SdkHttpRequest = {
    val signer: AwsV4HttpSigner = AwsV4HttpSigner.create()
    val signRequest             = SignRequest
      .builder(credentials)
      .request(request)
      .putProperty(AwsV4FamilyHttpSigner.AUTH_LOCATION, AuthLocation.QUERY_STRING)
      .putProperty(AwsV4FamilyHttpSigner.EXPIRATION_DURATION, expiration.toJava)
      .putProperty(AwsV4FamilyHttpSigner.SERVICE_SIGNING_NAME, ElastiCacheIamSigner.SERVICE_NAME)
      .putProperty(AwsV4HttpSigner.REGION_NAME, Region.of(auth.awsRegion).id())
      .build()
    signer.sign(signRequest).request()
  }

  private def createConnectionRequest: SdkHttpFullRequest = {
    SdkHttpFullRequest
      .builder()
      .method(SdkHttpMethod.GET)
      .uri(URI.create(s"${ElastiCacheIamSigner.REQUEST_PROTOCOL}${auth.awsReplicationGroupId}/"))
      .putRawQueryParameter(ElastiCacheIamSigner.PARAM_ACTION, Collections.singletonList(ElastiCacheIamSigner.ACTION_NAME))
      .putRawQueryParameter(ElastiCacheIamSigner.PARAM_USER, Collections.singletonList(auth.awsUserId))
      .build()
  }

  // Signing with an old signer (code from the ElastiCache docs)
  //  @nowarn("cat=deprecation")
  //  private def signV1(request: SdkHttpFullRequest, credentials: AwsCredentials, expiration: FiniteDuration): SdkHttpRequest = {
  //    import software.amazon.awssdk.auth.signer.Aws4Signer
  //    val now                = clock.nowOffset()
  //    val exp                = now.plusSeconds(expiration.toSeconds)
  //    val signer: Aws4Signer = Aws4Signer.create()
  //    val params: Aws4PresignerParams = Aws4PresignerParams
  //      .builder()
  //      .signingRegion(Region.of(auth.awsRegion))
  //      .signingName(ElastiCacheIamSigner.SERVICE_NAME)
  //      .expirationTime(exp.toInstant)
  //      .awsCredentials(credentials)
  //      .build()
  //    signer.presign(request, params)
  //  }
}

object ElastiCacheIamSigner {
  private val REQUEST_PROTOCOL: String = "http://"
  private val PARAM_ACTION: String     = "Action"
  private val PARAM_USER: String       = "User"
  private val ACTION_NAME: String      = "connect"
  private val SERVICE_NAME: String     = "elasticache"
}
