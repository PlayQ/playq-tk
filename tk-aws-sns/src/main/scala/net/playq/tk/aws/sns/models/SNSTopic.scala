package net.playq.tk.aws.sns.models
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.tagging.AwsNameSpace

abstract class SNSTopicId(
  name: String,
  serviceName: ServiceName,
)(implicit namespace: AwsNameSpace
) {
  def tags: Map[String, String] = namespace.serviceTags(serviceName)
  def prefix: String            = s"${namespace.toString}$serviceName-"
  def topicName: String         = s"$prefix$name"
  override def toString: String = topicName
}

final case class SNSTopic(
  arn: String,
  topicId: SNSTopicId,
)
