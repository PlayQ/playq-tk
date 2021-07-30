package net.playq.tk.aws.cost.config

final case class CostConfig(
  private val region: Option[String]
) {
  def getRegion: Option[String] = region.filter(_.nonEmpty)
}
