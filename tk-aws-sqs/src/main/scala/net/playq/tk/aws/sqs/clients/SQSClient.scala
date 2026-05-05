package net.playq.tk.aws.sqs.clients

import distage.{ModuleDef, Tag, TagKK}
import fs2.Stream
import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import izumi.distage.constructors.ClassConstructor
import izumi.functional.bio.Clock1.ClockAccuracy
import izumi.functional.bio.{Async2, Clock1, Clock2, Entropy2, Error2, F, Temporal2}
import izumi.fundamentals.platform.strings.IzString.*
import izumi.idealingua.runtime.circe.IRTWithCirce
import logstage.LogIO2
import net.playq.tk.aws.sqs.clients.SQSClient.VirtualQueue
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.aws.sqs.{SQSComponent, SQSQueueId, SQSQueuesManager}
import net.playq.tk.control.Forever
import net.playq.tk.quantified.SyncThrowable
import software.amazon.awssdk.services.sqs.model.*

import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import scala.jdk.DurationConverters.JavaDurationOps
import scala.util.chaining.*

trait SQSClient[F[_, _], +QueueId <: SQSQueueId] {

  def queueUrl: String
  def queue: QueueId

  def send[T: Encoder](message: T, delay: FiniteDuration           = 0.seconds, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, String]
  def sendList[T: Encoder](message: List[T], delay: FiniteDuration = 0.seconds, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, Unit]
  def sendString(message: String, delay: FiniteDuration            = 0.seconds, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, String]
  def sendStringList(message: List[String], delay: FiniteDuration  = 0.seconds, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, Unit]

  /** If timeout is None - config.maxPollingTime wil be used. */
  def pollString(
    batchSize: Int,
    timeout: Option[FiniteDuration]             = None,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): F[Throwable, List[SQSMessage[String]]]
  def poll[T: Decoder](
    batchSize: Int,
    skipFailedToDecode: Boolean                 = false,
    timeout: Option[FiniteDuration]             = None,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): F[Throwable, List[SQSMessage[T]]]

  def stream[T: Decoder](
    batchSize: Int,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): Stream[F[Throwable, _], List[SQSMessage[T]]]
  def streamWithSkip[T: Decoder](
    batchSize: Int,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): Stream[F[Throwable, _], List[SQSMessage[T]]]
  def stringStream(
    batchSize: Int,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): Stream[F[Throwable, _], List[SQSMessage[String]]]

  def changeMessagesVisibility(meta: List[SQSMessageMeta], newTimeout: FiniteDuration): F[Throwable, Unit]

  def deleteMessage(meta: SQSMessageMeta): F[Throwable, Unit]
  def deleteMessages(meta: List[SQSMessageMeta]): F[Throwable, Unit]
  final def deleteMessages(messages: Iterable[SQSMessage[?]]): F[Throwable, Unit] = deleteMessages(messages.map(_.meta).toList)
  def purge: F[Throwable, Unit]

  def getAttributes(attributes: QueueAttributeName*): F[Throwable, Map[QueueAttributeName, String]]
  def getQueueArn: F[Throwable, String]

  def addPermissionsToArnResource(arn: String, actions: Set[String] = Set("sqs:SendMessage")): F[Throwable, Unit]

  def requestFor[Req: Encoder, Rsp: Decoder](virtualQueue: VirtualQueue, request: Req, timeout: FiniteDuration): F[Throwable, Rsp]
  final def request[Req: Encoder, Rsp: Decoder](request: Req, timeout: FiniteDuration): F[Throwable, Rsp] = {
    requestFor(VirtualQueue.default, request, timeout)
  }

  def processRequestsFor[Req: Decoder: Encoder, Rsp: Encoder](virtualQueue: VirtualQueue, sleep: FiniteDuration)(respond: Req => F[Throwable, Rsp]): F[Nothing, Forever]
  final def processRequests[Req: Decoder: Encoder, Rsp: Encoder](sleep: FiniteDuration)(respond: Req => F[Throwable, Rsp]): F[Nothing, Forever] = {
    processRequestsFor(VirtualQueue.default, sleep)(respond)
  }

  final def sendFor[T: Encoder](
    virtualQueue: VirtualQueue,
    message: T,
    delay: FiniteDuration           = 0.seconds,
    attributes: Map[String, String] = Map.empty[String, String],
  ): F[Throwable, String] = {
    send(message, delay, SQSClient.withVirtualQueueAttribute(virtualQueue, attributes))
  }
  final def sendListFor[T: Encoder](
    virtualQueue: VirtualQueue,
    message: List[T],
    delay: FiniteDuration           = 0.seconds,
    attributes: Map[String, String] = Map.empty[String, String],
  ): F[Throwable, Unit] = {
    sendList(message, delay, SQSClient.withVirtualQueueAttribute(virtualQueue, attributes))
  }
  final def sendStringFor(
    virtualQueue: VirtualQueue,
    message: String,
    delay: FiniteDuration           = 0.seconds,
    attributes: Map[String, String] = Map.empty[String, String],
  ): F[Throwable, String] = {
    sendString(message, delay, SQSClient.withVirtualQueueAttribute(virtualQueue, attributes))
  }
  final def sendStringListFor(
    virtualQueue: VirtualQueue,
    message: List[String],
    delay: FiniteDuration           = 0.seconds,
    attributes: Map[String, String] = Map.empty[String, String],
  ): F[Throwable, Unit] = {
    sendStringList(message, delay, SQSClient.withVirtualQueueAttribute(virtualQueue, attributes))
  }

  final def streamFor[T: Decoder](
    virtualQueue: VirtualQueue,
    batchSize: Int,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): Stream[F[Throwable, _], List[SQSMessage[T]]] = {
    stream(batchSize, SQSClient.withVirtualQueueAttribute(virtualQueue, filterAttributes), deleteOrphanedAfter)
  }
  final def streamWithSkipFor[T: Decoder](
    virtualQueue: VirtualQueue,
    batchSize: Int,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): Stream[F[Throwable, _], List[SQSMessage[T]]] = {
    streamWithSkip(batchSize, SQSClient.withVirtualQueueAttribute(virtualQueue, filterAttributes), deleteOrphanedAfter)
  }
  final def stringStreamFor(
    virtualQueue: VirtualQueue,
    batchSize: Int,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): Stream[F[Throwable, _], List[SQSMessage[String]]] = {
    stringStream(batchSize, SQSClient.withVirtualQueueAttribute(virtualQueue, filterAttributes), deleteOrphanedAfter)
  }

  final def pollStringFor(
    virtualQueue: VirtualQueue,
    batchSize: Int,
    timeout: Option[FiniteDuration]             = None,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): F[Throwable, List[SQSMessage[String]]] = {
    pollString(batchSize, timeout, SQSClient.withVirtualQueueAttribute(virtualQueue, filterAttributes), deleteOrphanedAfter)
  }

  final def pollFor[T: Decoder](
    virtualQueue: VirtualQueue,
    batchSize: Int,
    skipFailedToDecode: Boolean                 = false,
    timeout: Option[FiniteDuration]             = None,
    filterAttributes: Map[String, String]       = Map.empty[String, String],
    deleteOrphanedAfter: Option[FiniteDuration] = None,
  ): F[Throwable, List[SQSMessage[T]]] = {
    poll(batchSize, skipFailedToDecode, timeout, SQSClient.withVirtualQueueAttribute(virtualQueue, filterAttributes), deleteOrphanedAfter)
  }
}

object SQSClient {

  final case class VirtualQueue(name: String) {
    override def toString: String         = name
    def suffix(suf: String): VirtualQueue = VirtualQueue(s"$name-$suf")
  }
  object VirtualQueue {
    val default: VirtualQueue = VirtualQueue("default")
  }

  final val virtualQueueAttributeName                                                                                     = "__Sys_Virtual_Queue_Name__"
  final val sentAtAttributeName                                                                                           = "__Sys_Sent_At__"
  final val responseQueueAttributeName                                                                                    = "__Sys_Response_Queue__"
  private def withVirtualQueueAttribute(virtualQueue: VirtualQueue, attributes: Map[String, String]): Map[String, String] = {
    attributes ++ Map(virtualQueueAttributeName -> virtualQueue.name)
  }

  def makeQueue[F[+_, +_]: TagKK, QID <: SQSQueueId: Tag: ClassConstructor](implicit mutCtx: ModuleDef#MutationContext): Unit = new mutCtx.dsl {
    make[QID]
    make[SQSClient[F, QID]].fromEffect((_: SQSQueuesManager[F]).subscribedClient(_: QID))

    many[SQSQueueId]
      .weak[QID]
  }.discard()

  final class Impl[F[+_, +_]: Async2: Temporal2: SyncThrowable, +QueueId <: SQSQueueId](
    sqsConfig: SQSConfig,
    sqsComponent: SQSComponent[F],
  )(override val queue: QueueId,
    override val queueUrl: String,
  )(implicit
    log: LogIO2[F],
    clock: Clock2[F],
    entropy: Entropy2[F],
  ) extends SQSClient[F, QueueId] {

    override def sendList[T: Encoder](message: List[T], delay: FiniteDuration, attributes: Map[String, String]): F[Throwable, Unit] = {
      sendStringList(message.map(_.asJson.noSpaces), delay, attributes)
    }

    override def sendStringList(messageList: List[String], delay: FiniteDuration, attributes: Map[String, String]): F[Throwable, Unit] = {
      F.when(messageList.nonEmpty) {
        for {
          now               <- clock.nowZoned().map(_.toEpochSecond.toString)
          batches            = messageList.grouped(5).toList
          adjustedAttributes = attributes ++ Map(sentAtAttributeName -> now)
          sqsAttributes      = adjustedAttributes.view.mapValues(MessageAttributeValue.builder.dataType("String").stringValue(_).build).toMap.asJava
          _                 <- log.debug(s"SQS: going to send ${batches.size -> "batches"} by 5. ${messageList.size -> "Total messages"}")
          _                 <- F.traverse_(batches) {
            message =>
              for {
                _ <- log.debug(s"SQS: Sending batch to $queue ${message.size -> "count"}.")

                messagesBatch = message.map {
                  m =>
                    SendMessageBatchRequestEntry.builder
                      .id(UUID.randomUUID().toString)
                      .messageBody(m)
                      .delaySeconds(delay.toSeconds.toInt)
                      .messageAttributes(sqsAttributes)
                      .build
                }.asJava

                req = SendMessageBatchRequest.builder.queueUrl(queueUrl).entries(messagesBatch).build

                _ <- sqsComponent
                  .rawClientRequest("send-messages-batch")(_.sendMessageBatch(req))
                  .logThrowable("SendMessageBatch", queue)
              } yield ()
          }
        } yield ()
      }
    }

    override def sendString(message: String, delay: FiniteDuration, attributes: Map[String, String] = Map.empty[String, String]): F[Throwable, String] = {
      for {
        now               <- clock.nowZoned().map(_.toEpochSecond.toString)
        _                 <- log.debug(s"SQS: Sending to $queue $message.")
        adjustedAttributes = attributes ++ Map(sentAtAttributeName -> now)
        sqsAttributes      = adjustedAttributes.view.mapValues(MessageAttributeValue.builder.dataType("String").stringValue(_).build).toMap.asJava
        req                = SendMessageRequest.builder
          .queueUrl(queueUrl)
          .messageBody(message)
          .delaySeconds(delay.toSeconds.toInt)
          .messageAttributes(sqsAttributes)
          .build

        res <- sqsComponent
          .rawClientRequest("send-message")(_.sendMessage(req).messageId)
          .logThrowable("SendMessage", queue)
      } yield res
    }

    override def send[T: Encoder](message: T, delay: FiniteDuration, attributes: Map[String, String]): F[Throwable, String] = {
      sendString(message.asJson.noSpaces, delay, attributes)
    }

    override def addPermissionsToArnResource(arn: String, actions: Set[String] = Set("sqs:SendMessage")): F[Throwable, Unit] = {
      for {
        oldAttributes <- getAttributes(QueueAttributeName.POLICY)
        policy         = oldAttributes.get(QueueAttributeName.POLICY).flatMap(decode[Policy](_).toOption).getOrElse(Policy(Set.empty))
        queueArn      <- getQueueArn
        _             <-
          if (policy.Statement.exists(s => s.Condition.noSpaces.contains(arn) && s.Resource == queueArn)) {
            log.info(s"$queue already have policy with this $arn")
          } else {
            val condition = Json.obj("ArnLike" -> Json.obj("aws:SourceArn" -> Json.fromString(arn)))
            val principal = Json.fromString("*")
            val stmt      = Statement(s"statement-${Clock1.Standard.nowZoned().toEpochSecond}", actions.asJson, "Allow", queueArn, principal, condition)
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
        .pipe(r => if (attributes.nonEmpty) r.attributeNames(attributes*) else r.attributeNames(QueueAttributeName.ALL))
        .build
      sqsComponent
        .rawClientRequest("get-attributes")(_.getQueueAttributes(req))
        .logThrowable("GetQueueAttributes", queue)
        .map(_.attributes.asScala.toMap)
    }

    /** @param batchSize - Valid values is 1 up to 10. (AWS limitation) */
    override def poll[T: Decoder](
      batchSize: Int              = 1,
      skipFailedToDecode: Boolean = false,
      timeout: Option[FiniteDuration],
      filterAttributes: Map[String, String],
      deleteOrphanedAfter: Option[FiniteDuration] = None,
    ): F[Throwable, List[SQSMessage[T]]] = {
      for {
        stringMessages <- pollString(batchSize, timeout, filterAttributes, deleteOrphanedAfter)
        decoded         = stringMessages.map(m => m.meta -> decode[T](m.body))
        failed          = decoded.collect { case (m, Left(f)) => m -> f }
        good            = decoded.collect { case (m, Right(body)) => SQSMessage(body, m) }

        _ <-
          if (skipFailedToDecode) {
            F.when(failed.nonEmpty)(
              log.warn(s"Going to skip messages failed to decode: ${failed.size -> "num"}. ${failed.map(_._2.getMessage).niceList() -> "Failures "}")
            ) *> deleteMessages(failed.map(_._1))
          } else {
            F.when(failed.nonEmpty)(
              log.error(s"Failed to decode: ${failed.size -> "num"}. ${failed.map(_._2.getMessage).niceList() -> "Failures "}")
            ) *> F.traverse_(failed.headOption.map(_._2))(F.fail(_))
          }
      } yield good
    }

    /** @param batchSize - Valid values is 1 up to 10. (AWS limitation)
      * We will try to poll `batchSize` number of messages uses filtering.
      * In case if we wasn't able to pull `batchSize` of filtered messages and there is still a
      * room for the next poll before the deadline - then we wil make a 50 millis sleep to not overload CPU and make a new call with adjusted batch size.
      */
    override def pollString(
      batchSize: Int,
      timeout: Option[FiniteDuration],
      filterAttributes: Map[String, String],
      deleteOrphanedAfter: Option[FiniteDuration] = None,
    ): F[Throwable, List[SQSMessage[String]]] = {
      val size = math.max(1, math.min(10, batchSize))

      for {
        _ <- log.debug(s"SQS: polling $queue with $size.")
        // deadline of the timeout
        beginTime <- clock.nowZoned(ClockAccuracy.MILLIS)
        deadline   = beginTime.plusSeconds(timeout.getOrElse(sqsConfig.maxPollingTime).toSeconds)

        messages <- fs2.Stream
          .unfoldLoopEval(0) {
            taken =>
              for {
                // current call expiration timeout
                now       <- clock.nowZoned(ClockAccuracy.SECONDS)
                expiration = ChronoUnit.SECONDS.between(now, deadline).toInt
                // if we have room for a new call - perform one - otherwise return empty list
                messages <-
                  if (expiration > 0) {
                    // build a request with adjusted batch
                    val newBatchSize = size - taken
                    val req          = ReceiveMessageRequest.builder
                      .queueUrl(queueUrl)
                      .messageAttributeNames(".*")
                      .waitTimeSeconds(expiration)
                      .maxNumberOfMessages(newBatchSize)
                      .build()
                    sqsComponent
                      .rawClientRequest("receive-messages")(_.receiveMessage(req).messages.asScala)
                      .logThrowable("ReceiveMessage", queue)
                      .map(_.toList.map {
                        m =>
                          SQSMessage(
                            m.body,
                            SQSMessageMeta(m.messageId, m.receiptHandle, m.messageAttributes.asScala.map { case (k, v) => k -> v.stringValue }.toMap),
                          )
                      })
                  } else {
                    F.pure(Nil)
                  }
                // filter messages based on `filterAttributes`
                (accepted, filtered) =
                  if (filterAttributes.nonEmpty) {
                    messages.partition(m => filterAttributes.forall { case (k, v) => m.meta.attributes.get(k).contains(v) })
                  } else {
                    (messages, Nil)
                  }
                // find orphaned messages if `deleteOrphanedAfter` is provided
                (toReturn, toDelete) = deleteOrphanedAfter match {
                  case Some(ttl) =>
                    val d = now.minusSeconds(ttl.toSeconds).toEpochSecond
                    filtered.partition(m => m.meta.attributes.get(sentAtAttributeName).map(_.toLong).exists(_ > d))
                  case None =>
                    filtered -> Nil
                }
                _ <- F.when(toReturn.nonEmpty)(changeMessagesVisibility(toReturn.map(_.meta), 0.seconds))
                _ <- F.when(toDelete.nonEmpty)(deleteMessages(toDelete.map(_.meta)))
                _ <- F.when(messages.nonEmpty) {
                  log.debug(s"SQS: polling $queue completed. Got ${accepted.size -> "accepted"}, ${toReturn.size -> "filtered"}, ${toDelete.size -> "deleted"}.")
                }
                next = Option(accepted.size + taken).filter(_ < size && messages.nonEmpty && filterAttributes.nonEmpty)
                // sleep if next loop is scheduled so we won't overload CPU's
                _ <- F.when(next.nonEmpty)(F.sleep(50.millis))
              } yield accepted -> next
          }.compile.toList.map(_.flatten)
        endTime <- clock.nowZoned(ClockAccuracy.MILLIS)
        _       <- F.when(messages.nonEmpty) {
          val filters = filterAttributes.map { case (k, v) => s"$k: $v" }.mkString("[", ", ", "]")
          val time    = java.time.Duration.between(beginTime, endTime).toScala.toCoarsest
          log.debug(s"SQS: polling $queue completed on $filters. Took $time. Got ${messages.size -> "count"}.")
        }
      } yield messages
    }

    override def stream[T: Decoder](
      batchSize: Int = 1,
      filterAttributes: Map[String, String],
      deleteOrphanedAfter: Option[FiniteDuration] = None,
    ): Stream[F[Throwable, _], List[SQSMessage[T]]] = {
      Stream.repeatEval(poll(batchSize, filterAttributes = filterAttributes, deleteOrphanedAfter = deleteOrphanedAfter))
    }

    override def streamWithSkip[T: Decoder](
      batchSize: Int,
      filterAttributes: Map[String, String],
      deleteOrphanedAfter: Option[FiniteDuration] = None,
    ): Stream[F[Throwable, _], List[SQSMessage[T]]] = {
      Stream.repeatEval(poll(batchSize, skipFailedToDecode = true, filterAttributes = filterAttributes, deleteOrphanedAfter = deleteOrphanedAfter))
    }

    override def stringStream(
      batchSize: Int = 1,
      filterAttributes: Map[String, String],
      deleteOrphanedAfter: Option[FiniteDuration] = None,
    ): Stream[F[Throwable, _], List[SQSMessage[String]]] = {
      Stream.repeatEval(pollString(batchSize, filterAttributes = filterAttributes, deleteOrphanedAfter = deleteOrphanedAfter))
    }

    override def changeMessagesVisibility(meta: List[SQSMessageMeta], newTimeout: FiniteDuration): F[Throwable, Unit] = {
      F.when(meta.nonEmpty) {
        for {
          _      <- log.debug(s"SQS: changing visibility $queue $newTimeout $meta.")
          entries = meta.map {
            m =>
              ChangeMessageVisibilityBatchRequestEntry.builder.id(m.id).receiptHandle(m.receiptHandle).visibilityTimeout(newTimeout.toSeconds.toInt).build
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
        _  <- sqsComponent
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
          _      <- F.traverse(entries.grouped(10).toList) {
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
        _  <- sqsComponent
          .rawClientRequest("queue-purge") {
            _.purgeQueue(req)
          }.void
          .catchSome { case _: PurgeQueueInProgressException => F.unit }
          .logThrowable("PurgeQueue", queue)
      } yield ()
    }

    override def requestFor[Req: Encoder, Rsp: Decoder](virtualQueue: VirtualQueue, request: Req, timeout: FiniteDuration): F[Throwable, Rsp] = {
      for {
        id                  <- entropy.nextUUID().map(_.toString)
        requestVirtualQueue  = requestsQueue(virtualQueue)
        responseVirtualQueue = virtualQueue.suffix(s"response-$id")
        _                   <- sendFor(requestVirtualQueue, request, attributes = Map(responseQueueAttributeName -> responseVirtualQueue.name))
        list                <- F
          .iterateWhile(pollFor(responseVirtualQueue, 10, timeout = Some(1.seconds)) <* F.sleep(300.millis))(_.size != 1)
          .timeoutFail(new TimeoutException("SQS request failed with timeout."))(timeout)
        res <- F.fromOption(new IllegalArgumentException("SQS response should have only one message."))(list.headOption)
        _   <- deleteMessage(res.meta)
      } yield res.body
    }

    override def processRequestsFor[Req: Decoder: Encoder, Rsp: Encoder](
      virtualQueue: VirtualQueue,
      sleep: FiniteDuration,
    )(respond: Req => F[Throwable, Rsp]
    ): F[Nothing, Forever] = {
      val requestsQueueName = requestsQueue(virtualQueue)
      Forever.loopForeverWithLog(s"SQS: process responses of ${queue.queueName}#$virtualQueue") {
        for {
          requests <- pollFor[Req](requestsQueueName, 10, skipFailedToDecode = true, deleteOrphanedAfter = Some(1.hour))
          _        <- deleteMessages(requests)
          _        <- F.traverse(requests) {
            request =>
              F.traverse(request.meta.attributes.get(responseQueueAttributeName)) {
                responseQueue =>
                  for {
                    response <- respond(request.body)
                    _        <- sendFor(VirtualQueue(responseQueue), response)
                  } yield ()
              }.sandboxExit
          }
          _ <- F.sleep(sleep)
        } yield ()
      }
    }

    private[this] def requestsQueue(virtualQueue: VirtualQueue): VirtualQueue = virtualQueue.suffix("requests")
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
          val cause = Option(failure.getCause).map(_.getMessage).getOrElse("N/A")
          log.error(s"SQS: Got error during executing $operation for ${queue.queueName}. ${failure.getMessage -> "Failure"}. Cause: $cause")
      }
    }
  }
}
