package net.playq.tk.aws.sqs.config

import scala.concurrent.duration.FiniteDuration

final case class SQSConfig(
  private val endpoint: Option[String],
  private val region: Option[String],
  maxPollingTime: FiniteDuration,
  queueAttributes: Option[Map[String, String]],
) {
  def getEndpoint: Option[String] = endpoint.filter(_.nonEmpty)
  def getRegion: Option[String]   = region.filter(_.nonEmpty)
}
