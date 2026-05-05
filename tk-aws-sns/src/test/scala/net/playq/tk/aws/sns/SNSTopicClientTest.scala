package net.playq.tk.aws.sns

import distage.{DefaultModule2, ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import izumi.functional.bio.Async2
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.sns.SNSTopicClientTest.Ctx
import net.playq.tk.aws.sns.test.SNSTestEnv
import net.playq.tk.aws.sqs.SQSQueueId
import net.playq.tk.aws.sqs.test.SQSTestEnv
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.test.TkTestBaseCtx
import software.amazon.awssdk.services.sns.model.ListSubscriptionsRequest
import zio.IO

import scala.jdk.CollectionConverters.ListHasAsScala

final class SNSTopicClientTestProd extends SNSTopicClientTest[IO] with SNSTestEnv[IO] with SQSTestEnv[IO]

object SNSTopicClientTest {
  final case class Ctx[F[+_, +_]](
    snsClient: SNSTopicClient[F],
    snsComponent: SNSComponent[F],
  )
}

abstract class SNSTopicClientTest[F[+_, +_]: Async2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    SNSTopicClient.makeTopic[F, TestSNSTopicId]
  }

  "SNSTopicClient" should {
    "subscribe sqs queue to a topic, sqs must receive message" in bound {
      ctx =>
        import ctx.*
        implicit val testNameSpace: AwsNameSpace = AwsNameSpace.newTestNameSpace()
        val sqsQueueId                           = new SQSQueueId("test-queue", ServiceName("test-queue")) {}
        for {
          (client, _)   <- snsClient.subscribeQueue(sqsQueueId)
          _             <- snsClient.publishString("message")
          queueMessages <- client.pollString(1)
          _             <- assertIO(queueMessages.exists(_.body == "message"))
        } yield ()
    }

    "subscribe sqs queue to a topic sqs must receive message batch" in bound {
      ctx =>
        import ctx.*
        implicit val testNameSpace: AwsNameSpace = AwsNameSpace.newTestNameSpace()
        val sqsQueueId                           = new SQSQueueId("test-queue", ServiceName("test-queue")) {}
        for {
          (client, _)   <- snsClient.subscribeQueue(sqsQueueId)
          _             <- snsClient.publishStingList(List("message", "message1"), 5)
          queueMessages <- client.pollString(2).map(_.map(_.body))
          _             <- assertIO(queueMessages.contains("message") && queueMessages.contains("message1"))
        } yield ()
    }

    "create/delete subscription" in bound {
      ctx =>
        import ctx.*
        implicit val testNameSpace: AwsNameSpace = AwsNameSpace.newTestNameSpace()
        val sqsQueueId                           = new SQSQueueId("test-queue", ServiceName("test-queue")) {}
        for {
          (_, subArn)    <- snsClient.subscribeQueue(sqsQueueId)
          subscriptions1 <- snsComponent.rawRequest("")(
            _.listSubscriptions(
              ListSubscriptionsRequest
                .builder()
                .build()
            )
          )
          _              <- assertIO(subscriptions1.subscriptions().asScala.exists(_.subscriptionArn() == subArn.arn))
          _              <- snsClient.unsubscribe(subArn)
          subscriptions2 <- snsComponent.rawRequest("")(
            _.listSubscriptions(
              ListSubscriptionsRequest
                .builder()
                .build()
            )
          )
          _ <- assertIO(!subscriptions2.subscriptions().asScala.exists(_.subscriptionArn() == subArn.arn))
        } yield ()
    }

  }
}
