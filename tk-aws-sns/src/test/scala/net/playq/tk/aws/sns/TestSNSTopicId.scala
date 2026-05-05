package net.playq.tk.aws.sns
import net.playq.tk.aws.sns.models.SNSTopicId
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.tagging.AwsNameSpace

import java.util.UUID

final class TestSNSTopicId(implicit awsNameSpace: AwsNameSpace) extends SNSTopicId(s"test-topic-${UUID.randomUUID()}", ServiceName("test"))
