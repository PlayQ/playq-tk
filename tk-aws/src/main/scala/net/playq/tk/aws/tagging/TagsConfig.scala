package net.playq.tk.aws.tagging

import net.playq.tk.aws.common.ServiceName

final case class TagsConfig(environment: String, deployment: String) {
  def tagService(serviceName: ServiceName): Map[String, String] = serviceName match {
    case ServiceName.Empty              => tags
    case ServiceName.Named(serviceName) => tags + ("service" -> serviceName)
  }
  val tags: Map[String, String] = Map(
    "environment" -> environment,
    "deployment"  -> deployment,
  )
}
