package net.playq.tk.aws.s3.config

import net.playq.tk.aws.common.ServiceName
import S3Config.S3BucketConfig
import net.playq.aws.tagging.AwsNameSpace

import scala.concurrent.duration.FiniteDuration

final case class S3Config(
  private val url: Option[String],
  private val region: Option[String],
  connectionTimeout: FiniteDuration,
  buckets: List[S3BucketConfig],
) {
  def getEndpoint: Option[String] = url.filter(_.nonEmpty)
  def getRegion: Option[String]   = region.filter(_.nonEmpty)
}

object S3Config {
  private[s3] def generateName(bucketName: String, serviceName: ServiceName, namespace: AwsNameSpace): String = {
    s"$bucketName-$serviceName-${namespace}playq"
  }

  final case class S3BucketConfig(
    bucketName: String,
    private val overrideName: Option[String],
    serviceCreatesBucket: Option[Boolean],
  ) {
    def nameFor(serviceName: ServiceName, namespace: AwsNameSpace): String = {
      overrideName.filter(_.nonEmpty).getOrElse(S3Config.generateName(bucketName, serviceName, namespace))
    }
  }
}
