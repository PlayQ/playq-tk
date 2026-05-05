package net.playq.tk.aws.sns

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.Async2
import logstage.LogIO2
import net.playq.tk.aws.sns.SNSTopicClient.ThrowableSNSOps
import net.playq.tk.aws.sns.models.{SNSTopic, SNSTopicId}
import net.playq.tk.aws.sqs.SQSQueuesManager
import software.amazon.awssdk.services.sns.model.{CreateTopicRequest, DeleteTopicRequest, Tag, TagResourceRequest}

import scala.jdk.CollectionConverters.{IterableHasAsJava, MapHasAsJava}

trait SNSTopicsManager[F[+_, +_]] {

  def regionBased(region: String): Lifecycle[F[Throwable, _], SNSTopicsManager[F]]

  def createTopic(snsTopicId: SNSTopicId, attributes: Map[String, String] = Map.empty): F[Throwable, SNSTopic]
  def deleteTopic(topicArn: SNSTopic): F[Throwable, Unit]

  def createClientForTopic[TI <: SNSTopicId](name: TI, attributes: Map[String, String] = Map.empty): F[Throwable, SNSTopicClient[F]]
}

object SNSTopicsManager {
  final class Impl[F[+_, +_]: Async2](
    snsComponent: SNSComponent[F],
    snsComponentFactory: SNSComponentFactory[F],
    sqsQueuesManager: SQSQueuesManager[F],
  )(implicit
    log: LogIO2[F]
  ) extends SNSTopicsManager[F] {

    override def regionBased(region: String): Lifecycle[F[Throwable, _], SNSTopicsManager[F]] =
      for {
        _            <- Lifecycle.liftF(log.info(s"SNS: going to create topics' manager in $region"))
        snsComponent <- snsComponentFactory.mkComponent(Some(region))
      } yield new Impl[F](snsComponent, snsComponentFactory, sqsQueuesManager)

    override def createClientForTopic[TID <: SNSTopicId](snsTopicId: TID, attributes: Map[String, String]): F[Throwable, SNSTopicClient[F]] = {
      for {
        topicArn <- createTopic(snsTopicId, attributes)
      } yield SNSTopicClient.Impl(topicArn.arn, snsComponent, sqsQueuesManager)
    }

    override def createTopic(snsTopicId: SNSTopicId, attributes: Map[String, String]): F[Throwable, SNSTopic] = {
      for {
        _ <- log.info(s"SNS: going to create topic with name: ${snsTopicId.topicName}")

        createTopicRequest = CreateTopicRequest
          .builder()
          .name(snsTopicId.topicName)
          .attributes(attributes.asJava)
          .build()

        topicArn <- snsComponent
          .rawRequest("createTopic")(_.createTopic(createTopicRequest).topicArn())
          .logThrowable("creating topic", s"name - ${snsTopicId.topicName}")

        tags               = snsTopicId.tags.map(entry => Tag.builder().key(entry._1).value(entry._2).build).asJavaCollection
        tagResourceRequest = TagResourceRequest
          .builder()
          .tags(tags)
          .resourceArn(topicArn)
          .build()

        _ <- snsComponent
          .rawRequest("tagging topic")(_.tagResource(tagResourceRequest))
          .logThrowable("tagging topic", topicArn)
      } yield SNSTopic(topicArn, snsTopicId)
    }

    override def deleteTopic(topicArn: SNSTopic): F[Throwable, Unit] = {
      for {
        _                 <- log.info(s"SNS: going to delete topic: $topicArn")
        deleteTopicRequest = DeleteTopicRequest
          .builder()
          .topicArn(topicArn.arn)
          .build()
        _ <- snsComponent
          .rawRequest("deleteTopic")(_.deleteTopic(deleteTopicRequest))
          .logThrowable("deleting topic", topicArn.arn)
          .void
      } yield ()
    }
  }
}
