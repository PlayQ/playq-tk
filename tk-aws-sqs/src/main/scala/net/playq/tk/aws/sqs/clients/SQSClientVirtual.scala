package net.playq.tk.aws.sqs.clients

import fs2._
import izumi.functional.bio.{Clock2, F, IO2}
import izumi.fundamentals.platform.time.IzTime
import logstage.LogIO2
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.aws.sqs.{SQSComponent, SQSQueueId}
import net.playq.tk.quantified.{ConcurrentThrowable, TimerThrowable}
import software.amazon.awssdk.services.sqs.model.QueueAttributeName

import java.time.ZonedDateTime

final class SQSClientVirtual[F[+_, +_]: IO2: ConcurrentThrowable: TimerThrowable, HostQueue <: SQSQueueId, QueueID <: SQSQueueId.Virtual[HostQueue]](
  sqsConfig: SQSConfig,
  sqsComponent: SQSComponent[F],
  hostClient: SQSClient[F, HostQueue],
  clock: Clock2[F],
)(queueID: QueueID
)(implicit log: LogIO2[F]
) extends SQSClientBase.AbstractSQSClient[F, QueueID](sqsConfig, sqsComponent)(queueID, hostClient.queueUrl) {
  private[this] def additionalMessageAttributes(now: ZonedDateTime) = Map(
    SQSClientVirtual.virtualQueueAttributeKey -> queueID.name,
    SQSClientVirtual.sentAtAttributeKey       -> now.format(IzTime.ISO_OFFSET_DATE_TIME_3NANO),
  )

  private[this] def updateAttributes(attributes: Map[String, String], now: ZonedDateTime): Map[String, String] = {
    attributes ++ additionalMessageAttributes(now)
  }

  override def sendString(message: String, delay: Int, attributes: Map[String, String]): F[Throwable, String] = {
    clock.now().flatMap {
      now =>
        hostClient.sendString(message, delay, updateAttributes(attributes, now))
    }
  }

  override def sendStringList(message: List[String], delay: Int, attributes: Map[String, String]): F[Throwable, Unit] = {
    clock.now().flatMap {
      now =>
        hostClient.sendStringList(message, delay, updateAttributes(attributes, now))
    }
  }

  override def pollString(batchSize: Int): F[Throwable, List[SQSMessage[String]]] = {
    Stream
      .unfoldLoopEval(0) {
        prev =>
          hostClient.pollStringWithTimeout(batchSize - prev, sqsConfig.maxPollingTime / 5).map {
            messages =>
              val (toTake, toReturn) = messages.partition(_.meta.attributes.get(SQSClientVirtual.virtualQueueAttributeKey).contains(queueID.name))
              (toTake, toReturn) -> Option(toTake.size + prev).filter(num => messages.nonEmpty && num < batchSize)
          }
      }.interruptAfter(sqsConfig.maxPollingTime).compile.toList.flatMap {
        all =>
          for {
            now             <- clock.now()
            expired          = now.minusHours(1)
            (toTake, others) = all.unzip
            (toReturn, toDelete) = others.flatten.partition {
              _.meta.attributes.get(SQSClientVirtual.sentAtAttributeKey).map(ZonedDateTime.parse(_, IzTime.ISO_OFFSET_DATE_TIME_3NANO)).exists(_.isAfter(expired))
            }
            _ <- changeMessagesVisibility(toReturn.map(_.meta), 0)
            _ <- deleteMessages(toDelete)
          } yield toTake.flatten
      }
  }

  override def changeMessagesVisibility(meta: List[SQSMessageMeta], newTimeout: Int): F[Throwable, Unit] = {
    hostClient.changeMessagesVisibility(meta, newTimeout)
  }

  override def deleteMessage(meta: SQSMessageMeta): F[Throwable, Unit] = {
    hostClient.deleteMessage(meta)
  }

  override def deleteMessages(meta: List[SQSMessageMeta]): F[Throwable, Unit] = {
    hostClient.deleteMessages(meta)
  }

  override def purge: F[Throwable, Unit] = F.unit

  override def getAttributes(attributes: QueueAttributeName*): F[Throwable, Map[QueueAttributeName, String]] = {
    hostClient.getAttributes(attributes: _*)
  }

  override def getQueueArn: F[Throwable, String] = {
    hostClient.getQueueArn
  }

  override def addPermissionsToArnResource(arn: String, actions: Set[String]): F[Throwable, Unit] = {
    hostClient.addPermissionsToArnResource(arn, actions)
  }
}

object SQSClientVirtual {
  def virtualQueueAttribute(name: String) = Map(virtualQueueAttributeKey -> name)
  val virtualQueueAttributeKey            = "VIRTUAL_QUEUE_NAME"
  val sentAtAttributeKey                  = "VIRTUAL_SENT_AT"
}
