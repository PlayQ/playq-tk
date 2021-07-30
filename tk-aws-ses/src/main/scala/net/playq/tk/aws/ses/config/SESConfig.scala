package net.playq.tk.aws.ses.config

final case class SESConfig(
  private val endpointUrl: Option[String],
  private val region: Option[String],
  senderEmail: String,
) {
  def getEndpoint: Option[String] = endpointUrl.filter(_.nonEmpty)
  def getRegion: Option[String]   = region.filter(_.nonEmpty)

  private[ses] def isTestEnv: Boolean = getEndpoint.nonEmpty
}
