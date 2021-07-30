package net.playq.tk.aws.sqs.clients

final case class SQSMessageMeta(id: String, receiptHandle: String, attributes: Map[String, String])
