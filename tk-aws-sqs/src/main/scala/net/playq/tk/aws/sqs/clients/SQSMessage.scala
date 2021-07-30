package net.playq.tk.aws.sqs.clients

final case class SQSMessage[T](body: T, meta: SQSMessageMeta)
