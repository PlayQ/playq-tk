package net.playq.tk.aws.sagemaker.config

final case class SagemakerConfig(
  private val region: Option[String],
  roleArn: String,
) {
  def getRegion: Option[String] = region.filter(_.nonEmpty)
  val regionOrDefault: String   = getRegion.getOrElse("us-east-1")
}
