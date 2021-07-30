package net.playq.tk.aws.sqs

import izumi.functional.bio.{F, IO2}
import net.playq.tk.aws.sqs.clients.{SQSClient, SQSClientBase}

import java.util.concurrent.ConcurrentHashMap

final class SQSVirtualQueuesManager[F[+_, +_]: IO2, HostQueueID <: SQSQueueId](
  client: SQSClient[F, HostQueueID]
) {
  private val known = new ConcurrentHashMap[String, SQSClientBase[F, SQSQueueId.Virtual[HostQueueID]]]()

  def getVirtualClientByName(queueName: String): F[Throwable, SQSClientBase[F, SQSQueueId.Virtual[HostQueueID]]] = {
    for {
      mbClient <- F.sync(Option(known.get(queueName)))
      res <- mbClient match {
        case None =>
          for {
            q <- client.getVirtual(queueName)
            _ <- F.sync(known.put(queueName, q))
          } yield q
        case Some(q) => F.pure(q)
      }
    } yield res
  }
}
