package net.playq.tk.aws.sts.config

import scala.concurrent.duration.FiniteDuration

final case class STSConfig(
  private val endpoint: Option[String],
  tokensExpiration: FiniteDuration,
) {
  def getEndpoint: Option[String] = endpoint.filter(_.nonEmpty)
}
