package net.playq.tk.aws.sqs

import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.functional.bio.catz._
import izumi.functional.bio.{F, Fork2, IO2, Primitives2}
import izumi.functional.mono.Entropy
import izumi.fundamentals.platform.functional.Identity
import izumi.idealingua.runtime.circe.IRTWithCirce
import net.playq.tk.aws.common.ServiceName
import SQSClientTest.{Ctx, TestData, TestQueueId}
import net.playq.aws.tagging.AwsNameSpace
import net.playq.tk.aws.sqs.clients.SQSClient
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.aws.sqs.test.SQSTestEnv
import net.playq.tk.test.TkTestBaseCtx
import net.playq.tk.test.rnd.TkRndBase
import zio.IO

import java.util.UUID
import scala.collection.mutable

final class SQSClientTest extends SQSClientTestBase[IO] with SQSTestEnv

object SQSClientTest {

  final case class Ctx[F[+_, +_]](
    sqsClient: SQSClient[F, TestQueueId],
    sqsConfig: SQSConfig,
  )

  final class TestQueueId(
    rnd: Entropy[Identity]
  )(implicit
    nameSpace: AwsNameSpace
  ) extends SQSQueueId(s"test-queue-${rnd.nextInt()}", ServiceName("test"))

  final case class TestData(data: String)
  object TestData extends IRTWithCirce[TestData]
}

@SuppressWarnings(Array("UnsafeTraversableMethods"))
abstract class SQSClientTestBase[F[+_, +_]: IO2: Primitives2: Fork2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] with TkRndBase {

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    include(SQSClient.sqsModuleDef[F, TestQueueId])
  }

  "SQS client" should {
    "Perform base crud" in scopeIO {
      ctx =>
        import ctx._

        val testMessage = "test message"
        for {
          _    <- sqsClient.sendString(testMessage)
          res  <- sqsClient.pollString(1)
          _    <- assertIO(res.headOption.exists(_.body == testMessage))
          _    <- sqsClient.changeMessagesVisibility(res.map(_.meta), 10)
          _    <- sqsClient.deleteMessage(res.head.meta)
          res1 <- sqsClient.pollString(1)
          _    <- assertIO(res1.isEmpty)
        } yield ()
    }

    "skip failed to decode" in scopeIO {
      ctx =>
        import ctx._

        val testMessage = "test message"
        for {
          _   <- sqsClient.sendString(testMessage)
          _   <- sqsClient.send(TestData("some"))
          res <- sqsClient.poll[TestData](batchSize = 10, skipFailedToDecode = true)
          _   <- assertIO(res.size == 1)
          _   <- sqsClient.deleteMessages(res.map(_.meta))
        } yield ()
    }

    "Stream processing" in scopeIO {
      ctx =>
        import ctx._
        val msgsNum   = 20
        val batchSize = 10

        for {
          msgs   <- F.traverse(1 to msgsNum)(_ => F.random[UUID].map(_.toString))
          _      <- sqsClient.sendStringList(msgs)
          ref    <- F.syncThrowable(new mutable.Stack[String]())
          finish <- F.mkPromise[Nothing, Unit]
          fiber <-
            sqsClient
              .stringStream(batchSize).evalMap {
                m =>
                  for {
                    _ <- sqsClient.deleteMessages(m.map(_.meta))
                    _ <- F.syncThrowable(ref ++= m.map(_.body))
                    _ <- F.when(ref.size == msgs.size)(finish.succeed(()).void)
                  } yield ()
              }.compile.drain.fork
          _ <- finish.await
          _ <- fiber.interrupt
          _ <- F.traverse(msgs)(m => assertIO(ref.toSet.contains(m)))
        } yield ()
    }

    "Virtual queues" in scopeIO {
      ctx =>
        import ctx._

        val testMessage1 = "test message 1"
        val testMessage2 = "test message 2"
        for {
          virtualClient1 <- sqsClient.getVirtual("virtual_1")
          virtualClient2 <- sqsClient.getVirtual("virtual_2")

          _ <- sqsClient.sendString("test")

          _ <- virtualClient1.sendString(testMessage1)
          _ <- virtualClient2.sendString(testMessage2)

          res <- virtualClient1.pollString(5)
          _   <- assertIO(res.length == 1)
          _   <- assertIO(res.headOption.exists(_.body == testMessage1))

          res2 <- virtualClient2.pollString(5)
          _    <- assertIO(res2.length == 1)
          _    <- assertIO(res2.headOption.exists(_.body == testMessage2))

          // should delete all unknown messages
          res3 <- sqsClient.pollString(5)
          _    <- assertIO(res3.isEmpty)
        } yield ()
    }
  }
}
