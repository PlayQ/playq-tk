package net.playq.tk.aws.sns
import distage.{DefaultModule2, TagKK}
import izumi.functional.bio.Async2
import net.playq.tk.aws.sns.SNSTopicsManagerTest.Ctx
import net.playq.tk.aws.sns.test.SNSTestEnv
import net.playq.tk.aws.sqs.test.SQSTestEnv
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.test.TkTestBaseCtx
import zio.IO

import scala.jdk.CollectionConverters.ListHasAsScala

final class SNSTopicManagerTest extends SNSTopicsManagerTest[IO] with SNSTestEnv[IO] with SQSTestEnv[IO]

object SNSTopicsManagerTest {
  final case class Ctx[F[+_, +_]](
    topicsManager: SNSTopicsManager[F],
    snsComponent: SNSComponent[F],
  )
}

abstract class SNSTopicsManagerTest[F[+_, +_]: Async2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  "SNSTopicsManager" should {
    "create/delete sns topic" in bound {
      ctx =>
        import ctx.*
        implicit val testNameSpace: AwsNameSpace = AwsNameSpace.newTestNameSpace()
        for {
          topicArn <- topicsManager.createTopic(new TestSNSTopicId)
          topics   <- snsComponent.rawRequest("")(_.listTopics().topics())
          _        <- assertIO(topics.asScala.map(_.topicArn()).contains(topicArn.arn))
          _        <- topicsManager.deleteTopic(topicArn)
          topics2  <- snsComponent.rawRequest("")(_.listTopics())
          _        <- assertIO(!topics2.topics().asScala.map(_.topicArn()).contains(topicArn.arn))
        } yield ()
    }
  }
}
