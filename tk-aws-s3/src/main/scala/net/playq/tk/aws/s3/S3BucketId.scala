package net.playq.tk.aws.s3

import net.playq.tk.aws.common.ServiceName
import net.playq.aws.tagging.AwsNameSpace
import net.playq.tk.aws.common.TagsConfigOps._
import net.playq.tk.aws.s3.config.S3Config

sealed trait S3BucketId {
  def bucketName: String

  def serviceCreatesBucket: Boolean
}

object S3BucketId {

  /**
    * Static bucket names are constant and wil be used as "borrowed" buckets
    *
    * (Services will not create/delete `Static` buckets and will not perform health checks on them)
    */
  abstract class Static(override val bucketName: String) extends S3BucketId {
    override final def serviceCreatesBucket: Boolean = false
  }

  /**
    * Buckets defined using this class will use a name that's either
    *
    *   - a 'generated' name – composed of `AwsNamespace` + `ServiceId`,
    *
    *   - or, if [[config.S3Config.S3BucketConfig.overrideName]] is specified in config,
    *     this will be the name used for the bucket.
    *
    * Services own `GenWithOverride `buckets. Service will create these buckets when it starts. update tags and perform health checks.
    *
    * There is one exception — if [[config.S3Config.S3BucketConfig.overrideName]] is specified
    * and [[config.S3Config.S3BucketConfig.serviceCreatesBucket]] is `false`,
    * then service will not try to create or check the bucket – it will behave same as a [[Static]] bucket.
    */
  abstract class GenWithOverride(
    private[s3] val name: String,
    private val serviceName: ServiceName,
  )(implicit
    namespace: AwsNameSpace,
    s3Config: S3Config,
  ) extends S3BucketId {
    private[s3] def tags: Map[String, String] = namespace.tags.tagService(serviceName)

    override final lazy val (bucketName: String, serviceCreatesBucket: Boolean) = {
      s3Config.buckets.find(_.bucketName == name) match {
        case Some(bucketConfig) =>
          (bucketConfig.nameFor(serviceName, namespace), bucketConfig.serviceCreatesBucket.getOrElse(true))
        case None =>
          (S3Config.generateName(name, serviceName, namespace), true)
      }
    }
  }

}
