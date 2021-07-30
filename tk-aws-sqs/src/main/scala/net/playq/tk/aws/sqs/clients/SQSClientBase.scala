package net.playq.tk.aws.sqs.clients

import fs2.Stream
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import izumi.functional.bio.{Error2, F, IO2}
import izumi.fundamentals.platform.strings.IzString._
import izumi.fundamentals.platform.time.IzTime
import izumi.idealingua.runtime.circe.IRTWithCirce
import logstage.LogIO2
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.aws.sqs.{SQSComponent, SQSQueueId}
import software.amazon.awssdk.services.sqs.model._

import java.util.UUID
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.chaining._

trait SQSClientBase[F[+_, +_], +QueueId <: SQSQueueId] {
  def queueUrl: String
  def queue: QueueId

  def send[T: Encoder](message: T, delay: Int           = 0, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, String]
  def sendList[T: Encoder](message: List[T], delay: Int = 0, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, Unit]
  def sendString(message: String, delay: Int            = 0, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, String]
  def sendStringList(message: List[String], delay: Int  = 0, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, Unit]

  protected[sqs] def pollStringWithTimeout(batchSize: Int, timeout: FiniteDuration): F[Throwable, List[SQSMessage[String]]]
  def pollString(batchSize: Int): F[Throwable, List[SQSMessage[String]]]
  def poll[T: Decoder](batchSize: Int, skipFailedToDecode: Boolean = false): F[Throwable, List[SQSMessage[T]]]

  def stream[T: Decoder](batchSize: Int): Stream[F[Throwable, ?], List[SQSMessage[T]]]
  def streamWithSkip[T: Decoder](batchSize: Int): Stream[F[Throwable, ?], List[SQSMessage[T]]]
  def stringStream(batchSize: Int): Stream[F[Throwable, ?], List[SQSMessage[String]]]

  def changeMessagesVisibility(meta: List[SQSMessageMeta], newTimeout: Int): F[Throwable, Unit]

  def deleteMessage(meta: SQSMessageMeta): F[Throwable, Unit]
  def deleteMessages(meta: List[SQSMessageMeta]): F[Throwable, Unit]
  final def deleteMessages(messages: Iterable[SQSMessage[_]]): F[Throwable, Unit] = deleteMessages(messages.map(_.meta).toList)
  def purge: F[Throwable, Unit]

  def getAttributes(attributes: QueueAttributeName*): F[Throwable, Map[QueueAttributeName, String]]
  def getQueueArn: F[Throwable, String]

  def addPermissionsToArnResource(arn: String, actions: Set[String] = Set("sqs:SendMessage")): F[Throwable, Unit]
}

object SQSClientBase {
  abstract class AbstractSQSClient[F[+_, +_]: IO2, +QueueId <: SQSQueueId](
    sqsConfig: SQSConfig,
    sqsComponent: SQSComponent[F],
  )(override val queue: QueueId,
    override val queueUrl: String,
  )(implicit
    log: LogIO2[F]
  ) extends SQSClientBase[F, QueueId] {

    override def addPermissionsToArnResource(arn: String, actions: Set[String] = Set("sqs:SendMessage")): F[Throwable, Unit] = {
      for {
        oldAttributes <- getAttributes(QueueAttributeName.POLICY)
        policy         = oldAttributes.get(QueueAttributeName.POLICY).flatMap(decode[Policy](_).toOption).getOrElse(Policy(Set.empty))
        queueArn      <- getQueueArn
        _ <-
          if (policy.Statement.exists(s => s.Condition.noSpaces.contains(arn) && s.Resource == queueArn)) {
            log.info(s"$queue already have policy with this $arn")
          } else {
            val condition = Json.obj("ArnLike" -> Json.obj("aws:SourceArn" -> Json.fromString(arn)))
            val principal = Json.fromString("*")
            val stmt      = Statement(s"statement-${IzTime.utcNow.toEpochSecond}", actions.asJson, "Allow", queueArn, principal, condition)
            val newPolicy = Policy(policy.Statement + stmt)
            val req       = SetQueueAttributesRequest.builder.queueUrl(queueUrl).attributes(Map(QueueAttributeName.POLICY -> newPolicy.asJson.spaces2).asJava).build
            log.info(s"Going to add policy for $queue $arn. ${newPolicy.asJson.noSpaces -> "Policy"}") *>
            sqsComponent
              .rawClientRequest("add-permissions-to-arn") {
                _.setQueueAttributes(req)
              }.logThrowable("SetQueueAttributes", queue)
          }
      } yield ()
    }

    override def getQueueArn: F[Throwable, String] =
      getAttributes(QueueAttributeName.QUEUE_ARN).flatMap {
        map =>
          F.fromOption(new RuntimeException("Empty queue arn"))(map.get(QueueAttributeName.QUEUE_ARN))
      }

    override def getAttributes(attributes: QueueAttributeName*): F[Throwable, Map[QueueAttributeName, String]] = {
      val req = GetQueueAttributesRequest.builder
        .queueUrl(queueUrl)
        .pipe(r => if (attributes.nonEmpty) r.attributeNames(attributes: _*) else r.attributeNames(QueueAttributeName.ALL))
        .build
      sqsComponent
        .rawClientRequest("get-attributes")(_.getQueueAttributes(req))
        .logThrowable("GetQueueAttributes", queue)
        .map(_.attributes.asScala.toMap)
    }

    override def sendList[T: Encoder](message: List[T], delay: Int, attributes: Map[String, String]): F[Throwable, Unit] = {
      sendStringList(message.map(_.asJson.noSpaces), delay, attributes)
    }

    override def sendStringList(messageList: List[String], delay: Int, attributes: Map[String, String]): F[Throwable, Unit] = {
      F.when(messageList.nonEmpty) {
        val batches = messageList.grouped(5).toList
        log.debug(s"SQS: going to send ${batches.size -> "batches"} by 5. ${messageList.size -> "Total messages"}") *>
        F.traverse_(batches) {
          message =>
            for {
              _ <- log.debug(s"SQS: Sending batch to $queue ${message.size -> "count"}.")

              messagesBatch = message.map {
                m =>
                  SendMessageBatchRequestEntry.builder
                    .id(UUID.randomUUID().toString)
                    .messageBody(m)
                    .delaySeconds(delay)
                    .messageAttributes(attributes.view.mapValues(MessageAttributeValue.builder.dataType("String").stringValue(_).build).toMap.asJava)
                    .build
              }.asJava

              req = SendMessageBatchRequest.builder.queueUrl(queueUrl).entries(messagesBatch).build

              _ <- sqsComponent
                .rawClientRequest("send-messages-batch") {
                  _.sendMessageBatch(req)
                }.logThrowable("SendMessageBatch", queue)
            } yield ()
        }
      }
    }

    override def sendString(message: String, delay: Int = 0, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, String] = {
      for {
        _ <- log.debug(s"SQS: Sending to $queue $message.")
        req = SendMessageRequest.builder
          .queueUrl(queueUrl)
          .messageBody(message)
          .delaySeconds(delay)
          .messageAttributes(attributes.view.mapValues(MessageAttributeValue.builder.dataType("String").stringValue(_).build).toMap.asJava)
          .build

        res <- sqsComponent
          .rawClientRequest("send-message") {
            _.sendMessage(req).messageId
          }.logThrowable("SendMessage", queue)
      } yield res
    }

    override def send[T: Encoder](message: T, delay: Int, attributes: Map[String, String]): F[Throwable, String] = {
      sendString(message.asJson.noSpaces, delay, attributes)
    }

    /** @param batchSize - Valid values is 1 up to 10. (AWS limitation) */
    override def poll[T: Decoder](batchSize: Int = 1, skipFailedToDecode: Boolean = false): F[Throwable, List[SQSMessage[T]]] = {
      for {
        stringMessages <- pollString(batchSize)
        decoded = stringMessages.map {
          m =>
            m.meta -> parse(m.body).flatMap(_.as[T])
        }
        failed = decoded.collect { case (m, Left(f)) => m -> f }
        other  = decoded.collect { case (m, Right(body)) => SQSMessage(body, m) }
        _ <- F.when(failed.nonEmpty)(
          log.warn(s"Going to skip messages failed to decode: ${failed.size -> "num"}. ${failed.map(_._2.getMessage).niceList() -> "Failures "}")
        )
        _ <-
          if (skipFailedToDecode) {
            deleteMessages(failed.map(_._1))
          } else {
            F.traverse_(failed.headOption.map(_._2))(F.fail(_))
          }
      } yield other
    }

    override protected[sqs] def pollStringWithTimeout(batchSize: Int, timeout: FiniteDuration): F[Throwable, List[SQSMessage[String]]] = {
      val req = ReceiveMessageRequest.builder
        .queueUrl(queueUrl)
        .messageAttributeNames(".*")
        .waitTimeSeconds(timeout.toSeconds.toInt)
        .maxNumberOfMessages(batchSize)
        .build()

      for {
        _ <- log.debug(s"SQS: polling $queue with $batchSize.")
        messages <- sqsComponent
          .rawClientRequest("receive-messages") {
            _.receiveMessage(req).messages.asScala
          }.logThrowable("ReceiveMessage", queue)
        _ <- F.when(messages.nonEmpty)(log.info(s"SQS: polling $queue completed. Got ${messages.size -> "count"}."))
      } yield messages.toList.map {
        m =>
          SQSMessage(
            m.body,
            SQSMessageMeta(m.messageId, m.receiptHandle, m.messageAttributes.asScala.map { case (k, v) => k -> v.stringValue }.toMap),
          )
      }
    }

    /** @param batchSize - Valid values is 1 up to 10. (AWS limitation) */
    override def pollString(batchSize: Int): F[Throwable, List[SQSMessage[String]]] = {
      pollStringWithTimeout(batchSize, sqsConfig.maxPollingTime)
    }

    override def stream[T: Decoder](batchSize: Int = 1): Stream[F[Throwable, ?], List[SQSMessage[T]]] = {
      Stream.repeatEval(poll(batchSize))
    }

    override def streamWithSkip[T: Decoder](batchSize: Int): Stream[F[Throwable, ?], List[SQSMessage[T]]] = {
      Stream.repeatEval(poll(batchSize, skipFailedToDecode = true))
    }

    override def stringStream(batchSize: Int = 1): Stream[F[Throwable, ?], List[SQSMessage[String]]] = {
      Stream.repeatEval(pollString(batchSize))
    }

    override def changeMessagesVisibility(meta: List[SQSMessageMeta], newTimeout: Int): F[Throwable, Unit] = {
      F.when(meta.nonEmpty) {
        for {
          _ <- log.debug(s"SQS: changing visibility $queue $newTimeout $meta.")
          entries = meta.map {
            m =>
              ChangeMessageVisibilityBatchRequestEntry.builder.id(m.id).receiptHandle(m.receiptHandle).visibilityTimeout(newTimeout).build
          }
          _ <- F.traverse(entries.grouped(10).toList) {
            batch =>
              val req = ChangeMessageVisibilityBatchRequest.builder.queueUrl(queueUrl).entries(batch.asJava).build
              sqsComponent
                .rawClientRequest("change-visibility-batch") {
                  _.changeMessageVisibilityBatch(req)
                }.logThrowable("ChangeMessageVisibilityBatch", queue)
          }
        } yield ()
      }
    }

    override def deleteMessage(meta: SQSMessageMeta): F[Throwable, Unit] = {
      for {
        _  <- log.debug(s"SQS: Deleting message from $queue with $meta.")
        req = DeleteMessageRequest.builder.queueUrl(queueUrl).receiptHandle(meta.receiptHandle).build
        _ <- sqsComponent
          .rawClientRequest("delete-message") {
            _.deleteMessage(req)
          }.logThrowable("DeleteMessage", queue)
      } yield ()
    }

    override def deleteMessages(meta: List[SQSMessageMeta]): F[Throwable, Unit] = {
      F.when(meta.nonEmpty) {
        for {
          _      <- log.debug(s"SQS: Deleting messages from $queue ${meta.size -> "count"}.")
          entries = meta.map(m => DeleteMessageBatchRequestEntry.builder.id(m.id).receiptHandle(m.receiptHandle).build)
          _ <- F.traverse(entries.grouped(10).toList) {
            batch =>
              val req = DeleteMessageBatchRequest.builder.queueUrl(queueUrl).entries(batch.asJava).build
              sqsComponent
                .rawClientRequest("delete-messages-batch") {
                  _.deleteMessageBatch(req)
                }.logThrowable("DeleteMessageBatch", queue)
          }
        } yield ()
      }
    }

    override def purge: F[Throwable, Unit] = {
      for {
        _  <- log.debug(s"SQS: Purge $queue.")
        req = PurgeQueueRequest.builder.queueUrl(queueUrl).build
        _ <- sqsComponent
          .rawClientRequest("queue-purge") {
            _.purgeQueue(req)
          }.void
          .catchSome { case _: PurgeQueueInProgressException => F.unit }
          .logThrowable("PurgeQueue", queue)
      } yield ()
    }
  }

  final case class Policy(Version: String, Statement: Set[Statement])
  object Policy extends IRTWithCirce[Policy] {
    def empty: Policy                            = Policy(Set.empty)
    def apply(statement: Set[Statement]): Policy = Policy(Version = "2012-10-17", Statement = statement)
  }
  final case class Statement(Sid: String, Action: Json, Effect: String, Resource: String, Principal: Json, Condition: Json)
  object Statement extends IRTWithCirce[Statement]

  private[sqs] implicit final class ThrowableSQSOps[F[+_, +_], A](private val f: F[Throwable, A]) extends AnyVal {
    def logThrowable(operation: String, queue: SQSQueueId)(implicit F: Error2[F], log: LogIO2[F]): F[Throwable, A] = {
      f.tapError {
        failure =>
          log.error(s"SQS: Got error during executing $operation for ${queue.queueName}. ${failure.getMessage -> "Failure"}.")
      }
    }
  }
}
