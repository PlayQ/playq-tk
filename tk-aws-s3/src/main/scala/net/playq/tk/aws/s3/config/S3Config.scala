package net.playq.tk.aws.s3.config

import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.tagging.AwsNameSpace

import scala.concurrent.duration.FiniteDuration

final case class S3Config(
  private val url: Option[String],
  connectionTimeout: FiniteDuration,
) {
  def getEndpoint: Option[String] = url.filter(_.nonEmpty)
}

object S3Config {
  def generateName(bucketName: String, serviceName: ServiceName, namespace: AwsNameSpace): String = {
    s"$bucketName-$serviceName-$namespace${namespace.company.getOrElse("")}"
  }

  final case class S3BucketsConfig(buckets: List[S3BucketOverride]) {
    private[this] val bucketMap                   = buckets.map(b => b.bucketName -> b).toMap
    def get(bucketName: String): S3BucketOverride = bucketMap.getOrElse(bucketName, S3BucketOverride(bucketName, None, None))
  }

  final case class S3BucketOverride(
    bucketName: String,
    private val overrideName: Option[String],
    serviceCreatesBucket: Option[Boolean],
  ) {
    def nameFor(serviceName: ServiceName, namespace: AwsNameSpace): String = {
      overrideName.filter(_.nonEmpty).getOrElse(S3Config.generateName(bucketName, serviceName, namespace))
    }
  }
}
