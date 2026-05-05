package net.playq.tk.aws.s3

import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.s3.S3Bucket.CorsRule
import net.playq.tk.aws.s3.config.S3Config.S3BucketsConfig
import net.playq.tk.aws.s3.config.S3RetryConfig
import net.playq.tk.aws.tagging.AwsNameSpace

sealed trait S3BucketId {
  def bucketName: String
  def serviceCreatesBucket: Boolean
  def retryConfig: S3RetryConfig = S3RetryConfig.default
}

object S3BucketId {

  /**
    * Provided bucket names are constant and wil be used as "borrowed" buckets
    *
    * (Services will not create/delete `Provided` buckets and will not perform health checks on them)
    */
  abstract class Provided(override val bucketName: String) extends S3BucketId {
    override final def serviceCreatesBucket: Boolean = false
    override def toString: String                    = s"provided:$bucketName"
  }
  object Provided {
    def apply(bucketName: String): Provided = new Provided(bucketName) {}
  }

  /**
    * Buckets defined using this class will use a name that's either
    *
    *   - a 'generated' name – composed of `AwsNamespace` + `ServiceId`,
    *
    *   - or, if net.playq.tk.aws.s3.config.S3Config.S3BucketConfig.overrideName is specified in config,
    *     this will be the name used for the bucket.
    *
    * Services own `Managed` buckets. Service will create these buckets when it starts. update tags and perform health checks.
    *
    * There is one exception — if net.playq.tk.aws.s3.config.S3Config.S3BucketConfig.overrideName is specified
    * and net.playq.tk.aws.s3.config.S3Config.S3BucketConfig.serviceCreatesBucket is `false`,
    * then service will not try to create or check the bucket – it will behave same as a [[Provided]] bucket.
    */
  abstract class Managed(
    private[s3] val name: String,
    private val serviceName: ServiceName,
  )(implicit
    namespace: AwsNameSpace,
    s3BucketsConfig: S3BucketsConfig,
  ) extends S3BucketId {
    private[s3] final def tags: Map[String, String]                             = namespace.serviceTags(serviceName)
    override final lazy val (bucketName: String, serviceCreatesBucket: Boolean) = {
      val bucketConfig = s3BucketsConfig.get(name)
      (bucketConfig.nameFor(serviceName, namespace), bucketConfig.serviceCreatesBucket.getOrElse(true))
    }
    def cors: Set[CorsRule] = Set.empty

    override def toString: String = s"managed:$bucketName"
  }
}
