package net.playq.tk.aws.tagging

final case class TagsConfig(environment: String, deployment: String) {
  val tags = Map(
    "environment" -> environment,
    "deployment"  -> deployment,
  )
}
