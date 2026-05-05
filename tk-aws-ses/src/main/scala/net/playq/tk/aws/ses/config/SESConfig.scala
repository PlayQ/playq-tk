package net.playq.tk.aws.ses.config

final case class SESConfig(
  private val endpointUrl: Option[String],
  senderEmail: String,
) {
  def getEndpoint: Option[String]     = endpointUrl.filter(_.nonEmpty)
  private[ses] def isTestEnv: Boolean = getEndpoint.nonEmpty
}
