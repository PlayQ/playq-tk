package net.playq.tk.aws.sqs

import distage.Lifecycle
import izumi.functional.bio.{Async2, Clock2, F, Fork2, Primitives2, Temporal2}
import logstage.LogIO2
import net.playq.tk.aws.sqs.clients.SQSClient
import net.playq.tk.aws.sqs.clients.SQSClient.ThrowableSQSOps
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.quantified.AsyncThrowable
import software.amazon.awssdk.services.sqs.model.*

import scala.jdk.CollectionConverters.*

trait SQSQueuesManager[F[+_, +_]] {
  def regionBased(region: String): Lifecycle[F[Throwable, _], SQSQueuesManager[F]]

  def subscribedClient[QueueId <: SQSQueueId](queue: QueueId): F[Throwable, SQSClient[F, QueueId]]

  def createQueue(queue: SQSQueueId): F[Throwable, String]
  def getQueueUrl(queue: SQSQueueId): F[Throwable, String]
  def deleteQueue(queue: SQSQueueId): F[Throwable, Unit]

  def listDefaultQueuesUrl(): F[Throwable, List[String]]
}

object SQSQueuesManager {

  final class SQSQueuesManagerImpl[F[+_, +_]: Async2: Temporal2: Fork2: Primitives2: AsyncThrowable](
    sqsConfig: SQSConfig,
    sqsComponent: SQSComponent[F],
    sqsComponentFactory: SQSComponentFactory[F],
    healthChecker: SQSHealthChecker[F],
  )(implicit
    log: LogIO2[F],
    clock: Clock2[F],
  ) extends SQSQueuesManager[F] {

    override def regionBased(region: String): Lifecycle[F[Throwable, _], SQSQueuesManager[F]] = {
      for {
        _                 <- Lifecycle.liftF(log.info(s"Going to create SQS client in $region"): F[Throwable, Unit])
        regionalComponent <- sqsComponentFactory.mkComponent(Some(region))
      } yield new SQSQueuesManagerImpl(sqsConfig, regionalComponent, sqsComponentFactory, healthChecker)
    }

    override def getQueueUrl(queue: SQSQueueId): F[Throwable, String] = {
      sqsComponent
        .rawClientRequest("queue-get-url") {
          _.getQueueUrl(GetQueueUrlRequest.builder.queueName(queue.queueName).build).queueUrl()
        }.logThrowable("GetQueueUrl", queue)
    }

    override def subscribedClient[QueueId <: SQSQueueId](queue: QueueId): F[Throwable, SQSClient[F, QueueId]] = {
      for {
        _      <- log.info(s"SQS: Creating subscribed SQSClient. ${queue.queueName}")
        client <- getQueueUrl(queue)
          .catchAll(_ => createQueue(queue))
          .map(new SQSClient.Impl(sqsConfig, sqsComponent)(queue, _))
      } yield client
    }

    override def createQueue(queue: SQSQueueId): F[Throwable, String] = {
      for {
        _        <- log.info(s"SQS: Going to create $queue with ${queue.tags -> "tags"}.")
        queueUrl <- createQueueIfNotExists(queue)
        _        <- sqsComponent
          .rawClientRequest("queue-tag") {
            _.tagQueue(TagQueueRequest.builder.queueUrl(queueUrl).tags(queue.tags.asJava).build)
          }.logThrowable("TagQueue", queue)
      } yield queueUrl
    }

    override def deleteQueue(queue: SQSQueueId): F[Throwable, Unit] = {
      for {
        queueUrl <- getQueueUrl(queue)
        _        <- log.info(s"SQS: deleting $queue")
        req       = DeleteQueueRequest.builder.queueUrl(queueUrl).build
        _        <- sqsComponent.rawClientRequest("queue-delete")(_.deleteQueue(req)).logThrowable("DeleteQueue", queue)
      } yield ()
    }

    override def listDefaultQueuesUrl(): F[Throwable, List[String]] = {
      for {
        _   <- log.info("SQS: list queues")
        res <- sqsComponent.rawClientRequest("queue-list")(_.listQueues().queueUrls.asScala.toList).catchAll {
          f =>
            log.error(s"SQS: failure during listing queues: ${f -> "Failure"}.") *> F.fail(f)
        }
      } yield res
    }

    private[this] def createQueueIfNotExists(queue: SQSQueueId): F[Throwable, String] = {
      val request = CreateQueueRequest.builder
        .queueName(queue.queueName)
        .attributes(sqsConfig.queueAttributes.getOrElse(Map.empty).map { case (k, v) => QueueAttributeName.fromValue(k) -> v }.asJava)
        .build

      sqsComponent
        .rawClientRequest("queue-create")(_.createQueue(request).queueUrl())
        .logThrowable("CreateQueue", queue)
        .catchSome {
          case m: SqsException if m.getMessage.startsWith(ConcurrentAccessAlreadyExistsErrorMessage) => getQueueUrl(queue)
        }
    }
  }
  final val ConcurrentAccessAlreadyExistsErrorMessage = "Concurrent access: Queue already exists:"
}
