package net.playq.tk.aws.tagging

import net.playq.tk.aws.common.ServiceName

import java.util.UUID

final case class AwsNameSpace(namespace: Option[String], company: Option[String], tags: TagsConfig) {
  def serviceTags(service: ServiceName): Map[String, String] = tags.tagService(service)
  override def toString: String                              = namespace.filter(_.nonEmpty).fold("")(ns => s"$ns-")
}

object AwsNameSpace {
  def newTestNameSpace(randomString: String = UUID.randomUUID().toString.take(5)): AwsNameSpace = {
    AwsNameSpace(Some(s"test-$randomString"), Some("test-company"), TagsConfig("test", "test"))
  }
}
