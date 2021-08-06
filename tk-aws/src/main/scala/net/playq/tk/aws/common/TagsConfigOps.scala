package net.playq.tk.aws.common

import net.playq.tk.aws.tagging.TagsConfig

object TagsConfigOps {
  implicit final class TagService(private val cfg: TagsConfig) extends AnyVal {
    def tagService(serviceName: ServiceName): Map[String, String] = {
      cfg.tags + ("service" -> serviceName.serviceName)
    }
  }
}
