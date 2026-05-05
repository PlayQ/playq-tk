package net.playq.tk.http.config

final case class TkIdlHttpServiceConfig(
  idlRoutesPrefix: Option[String]
) {
  def prefix: String = idlRoutesPrefix.getOrElse("/v2")
}

object TkIdlHttpServiceConfig {
  def V2: TkIdlHttpServiceConfig    = TkIdlHttpServiceConfig(None)
  def apiV3: TkIdlHttpServiceConfig = TkIdlHttpServiceConfig(Some("/api/v3"))
}
