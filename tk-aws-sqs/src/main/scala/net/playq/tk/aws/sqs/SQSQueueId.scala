package net.playq.tk.aws.sqs

import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.common.TagsConfigOps.*
import net.playq.tk.aws.tagging.AwsNameSpace

abstract class SQSQueueId(
  private val queue: String,
  private val serviceName: ServiceName,
)(implicit private val namespace: AwsNameSpace
) {
  def tags: Map[String, String] = namespace.tags.tagService(serviceName)
  def queueName: String         = s"${namespace.toString}$serviceName-$queue"
  def dynamicRegion: Boolean    = false
  override def toString: String = queueName
}

object SQSQueueId {
  final case class Virtual[+QueueID <: SQSQueueId](name: String, parent: QueueID) extends SQSQueueId(s"${parent.queue}#$name", parent.serviceName)(parent.namespace)
}
