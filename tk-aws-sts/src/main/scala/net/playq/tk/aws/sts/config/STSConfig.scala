package net.playq.tk.aws.sts.config

import scala.concurrent.duration.FiniteDuration

final case class STSConfig(
  private val endpoint: Option[String],
  private val region: Option[String],
  tokensExpiration: FiniteDuration,
) {
  def getEndpoint: Option[String] = endpoint.filter(_.nonEmpty)
  def getRegion: Option[String]   = region.filter(_.nonEmpty)
}
