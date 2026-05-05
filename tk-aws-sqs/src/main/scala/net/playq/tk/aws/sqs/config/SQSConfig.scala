package net.playq.tk.aws.sqs.config

import scala.concurrent.duration.FiniteDuration

final case class SQSConfig(
  private val endpoint: Option[String],
  maxPollingTime: FiniteDuration,
  queueAttributes: Option[Map[String, String]],
) {
  def getEndpoint: Option[String] = endpoint.filter(_.nonEmpty)
}
