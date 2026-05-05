package net.playq.tk.aws.sns

import distage.{ModuleDef, Tag, TagKK}
import io.circe.Encoder
import io.circe.syntax.*
import izumi.distage.constructors.ClassConstructor
import izumi.functional.bio.{Async2, Error2, F}
import logstage.LogIO2
import net.playq.tk.aws.sns.models.{SNSTopicId, SubscriptionArn}
import net.playq.tk.aws.sqs.clients.SQSClient
import net.playq.tk.aws.sqs.{SQSQueueId, SQSQueuesManager}
import software.amazon.awssdk.services.sns.model.*

import java.util.UUID
import scala.jdk.CollectionConverters.{MapHasAsJava, SeqHasAsJava}

trait SNSTopicClient[F[+_, +_]] {

  def topicArn: String

  def subscribeUrl(url: String, attributes: Map[String, String]           = Map.empty): F[Throwable, SubscriptionArn]
  def subscribeQueue(queueId: SQSQueueId, attributes: Map[String, String] = Map.empty): F[Throwable, (SQSClient[F, SQSQueueId], SubscriptionArn)]
  def unsubscribe(subscriptionArn: SubscriptionArn): F[Throwable, Unit]

  def publish[T: Encoder](message: T, attributes: Map[String, String]                             = Map.empty): F[Throwable, Unit]
  def publishString(message: String, attributes: Map[String, String]                              = Map.empty): F[Throwable, Unit]
  def publishBatch[T: Encoder](messages: List[T], batchSize: Int, attributes: Map[String, String] = Map.empty): F[Throwable, Unit]
  def publishStingList(messages: List[String], batchSize: Int, attributes: Map[String, String]    = Map.empty): F[Throwable, Unit]
}

object SNSTopicClient {

  private val rawMessageDeliveryAttribute = Map("RawMessageDelivery" -> "true")

  def makeTopic[F[+_, +_]: TagKK, TID <: SNSTopicId: Tag: ClassConstructor](implicit mutCtx: ModuleDef#MutationContext): Unit = new mutCtx.dsl {
    make[TID]
    make[SNSTopicClient[F]].fromEffect((_: SNSTopicsManager[F]).createClientForTopic(_: TID))
    many[SNSTopicId]
      .weak[TID]
  }.discard()

  final case class Impl[F[+_, +_]: Async2](
    topicArn: String,
    component: SNSComponent[F],
    sqsQueuesManager: SQSQueuesManager[F],
  )(implicit log: LogIO2[F]
  ) extends SNSTopicClient[F] {

    override def subscribeUrl(
      url: String,
      attributes: Map[String, String],
    ): F[Throwable, SubscriptionArn] = {
      for {
        _ <- log.info(s"SNS: going to subscribe $url to $topicArn")

        adjustedAttributes = attributes ++ rawMessageDeliveryAttribute

        subscribeRequest = SubscribeRequest
          .builder()
          .protocol("http")
          .endpoint(url)
          .topicArn(topicArn)
          .attributes(adjustedAttributes.asJava)
          .build()

        arn <- component
          .rawRequest("subscribe")(_.subscribe(subscribeRequest).subscriptionArn())
          .map(SubscriptionArn.apply)
          .logThrowable(s"subscribing $url", topicArn)
      } yield arn
    }

    override def subscribeQueue(
      queueId: SQSQueueId,
      attributes: Map[String, String],
    ): F[Throwable, (SQSClient[F, SQSQueueId], SubscriptionArn)] = {
      for {
        _ <- log.info(s"SNS: going to subscribe sqs queue to $topicArn")

        sqsClient <- sqsQueuesManager.subscribedClient(queueId)
        _         <- sqsClient.addPermissionsToArnResource(topicArn)
        queueArn  <- sqsClient.getQueueArn

        adjustedAttributes = attributes ++ rawMessageDeliveryAttribute

        subscribeRequest = SubscribeRequest
          .builder()
          .protocol("sqs")
          .endpoint(queueArn)
          .topicArn(topicArn)
          .attributes(adjustedAttributes.asJava)
          .build

        arn <- component
          .rawRequest("subscribe")(_.subscribe(subscribeRequest).subscriptionArn())
          .map(SubscriptionArn.apply)
          .logThrowable(s"subscribing sqs queue $queueId", topicArn)
      } yield (sqsClient, arn)
    }

    override def unsubscribe(subscriptionArn: SubscriptionArn): F[Throwable, Unit] = {
      for {
        _ <- log.info(s"SNS: going to unsubscribe $subscriptionArn from $topicArn")

        unsubscribeRequest = UnsubscribeRequest
          .builder()
          .subscriptionArn(subscriptionArn.arn)
          .build()

        _ <- component
          .rawRequest("delete subscription")(_.unsubscribe(unsubscribeRequest).sdkHttpResponse())
          .logThrowable(s"delete $subscriptionArn", topicArn)
      } yield ()
    }

    override def publish[T: Encoder](message: T, attributes: Map[String, String]): F[Throwable, Unit] = {
      publishString(message.asJson.noSpaces, attributes)
    }

    /** @param batchSize - Valid values is 1 up to 10. (AWS limitation) */
    override def publishBatch[T: Encoder](
      messages: List[T],
      batchSize: Int,
      attributes: Map[String, String],
    ): F[Throwable, Unit] = {
      publishStingList(messages.map(_.asJson.noSpaces), batchSize, attributes)
    }

    override def publishString(message: String, attributes: Map[String, String]): F[Throwable, Unit] = {
      for {
        _ <- log.debug(s"""SNS: going to publish: 
                          | $message
                          | To $topicArn topic
                          | """.stripMargin)

        publishRequest = PublishRequest
          .builder()
          .topicArn(topicArn)
          .message(message)
          .messageAttributes(attributes.view.mapValues(MessageAttributeValue.builder.dataType("String").stringValue(_).build).toMap.asJava)
          .build()

        _ <- component
          .rawRequest("publish")(_.publish(publishRequest).messageId())
          .logThrowable(s"publishing $message", topicArn)
      } yield ()
    }

    /** @param batchSize - Valid values is 1 up to 10. (AWS limitation) */
    override def publishStingList(
      messages: List[String],
      batchSize: Int,
      attributes: Map[String, String],
    ): F[Throwable, Unit] = {
      val batches = messages.map {
        message =>
          PublishBatchRequestEntry
            .builder()
            .id(UUID.randomUUID().toString)
            .message(message)
            .messageAttributes(attributes.view.mapValues(MessageAttributeValue.builder.dataType("String").stringValue(_).build).toMap.asJava)
            .build()
      }.grouped(batchSize).toList

      for {
        _ <- log.debug(s"SQS: going to send ${batches.size -> "batches"} by $batchSize. ${messages.size -> "Total messages"}")
        _ <- F.traverse_(batches) {
          batch =>
            val publishBatchRequest = PublishBatchRequest
              .builder()
              .topicArn(topicArn)
              .publishBatchRequestEntries(batch.asJava)
              .build()

            component
              .rawRequest("publishBatch")(_.publishBatch(publishBatchRequest))
              .logThrowable(s"publishing batch -> size: ${batch.size}", topicArn)
              .void
        }
      } yield ()
    }
  }

  private[sns] implicit final class ThrowableSNSOps[F[+_, +_], A](private val f: F[Throwable, A]) extends AnyVal {
    def logThrowable(operation: String, topicArn: String)(implicit F: Error2[F], log: LogIO2[F]): F[Throwable, A] = {
      f.tapError {
        error =>
          val cause = Option(error.getCause).map(_.getMessage).getOrElse("")
          log.error(s"""\nSNS: Got error during $operation.
                       |Failure: ${error.getMessage}
                       |$topicArn
                       |$cause
                       |""".stripMargin)
      }
    }
  }
}
