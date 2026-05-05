package net.playq.tk.aws.sqs

import distage.{ModuleDef, TagKK}
import izumi.distage.model.definition.Module
import izumi.distage.modules.DefaultModule2
import izumi.distage.testkit.model.TestConfig
import izumi.functional.bio.{Async2, Entropy1, F, Fork2, Primitives2, Temporal2}
import izumi.fundamentals.platform.functional.Identity
import izumi.idealingua.runtime.circe.IRTWithCirce
import net.playq.tk.aws.common.ServiceName
import net.playq.tk.aws.sqs.SQSClientTest.*
import net.playq.tk.aws.sqs.clients.SQSClient
import net.playq.tk.aws.sqs.clients.SQSClient.VirtualQueue
import net.playq.tk.aws.sqs.config.SQSConfig
import net.playq.tk.aws.sqs.test.SQSTestEnv
import net.playq.tk.aws.tagging.AwsNameSpace
import net.playq.tk.quantified.ConcurrentThrowable
import net.playq.tk.test.TkTestBaseCtx
import net.playq.tk.test.rnd.TkRndBase
import zio.IO

import java.util.UUID
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

final class SQSClientTest extends SQSClientTestBase[IO] with SQSTestEnv[IO]

object SQSClientTest {

  final case class Ctx[F[+_, +_]](
    sqsClient: SQSClient[F, TestQueueId],
    queueManager: SQSQueuesManager[F],
    sqsConfig: SQSConfig,
  )(implicit
    val T: Temporal2[F],
    val C: ConcurrentThrowable[F],
  )

  final class TestQueueId(
    rnd: Entropy1[Identity]
  )(implicit
    nameSpace: AwsNameSpace
  ) extends SQSQueueId(s"test-queue-${rnd.nextInt()}", ServiceName("test"))

  final case class TestData(data: String)
  object TestData extends IRTWithCirce[TestData]

  final case class TestReq(data: String)
  object TestReq extends IRTWithCirce[TestReq]

  final case class TestRes(data: String)
  object TestRes extends IRTWithCirce[TestRes]
}

@SuppressWarnings(Array("UnsafeTraversableMethods"))
abstract class SQSClientTestBase[F[+_, +_]: Async2: Primitives2: Fork2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] with TkRndBase {

  override def moduleOverrides: Module = super.moduleOverrides ++ new ModuleDef {
    SQSClient.makeQueue[F, TestQueueId]
  }

  "SQS client" should {
    "Perform base crud" in bound {
      ctx =>
        import ctx.*

        val testMessage = "test message"
        for {
          _    <- sqsClient.sendString(testMessage)
          res  <- sqsClient.pollString(1)
          _    <- assertIO(res.headOption.exists(_.body == testMessage))
          _    <- sqsClient.changeMessagesVisibility(res.map(_.meta), 10.seconds)
          _    <- sqsClient.deleteMessage(res.head.meta)
          res1 <- sqsClient.pollString(1)
          _    <- assertIO(res1.isEmpty)
        } yield ()
    }

    "skip failed to decode" in bound {
      ctx =>
        import ctx.*

        val testMessage = "test message"
        for {
          _   <- sqsClient.sendString(testMessage)
          _   <- sqsClient.send(TestData("some"))
          res <- sqsClient.poll[TestData](batchSize = 10, skipFailedToDecode = true)
          _   <- assertIO(res.size == 1)
          _   <- sqsClient.deleteMessages(res.map(_.meta))
        } yield ()
    }

    "Stream processing" in bound {
      ctx =>
        import ctx.*
        val msgsNum   = 20
        val batchSize = 10

        for {
          msgs <- F.traverse(1 to msgsNum)(_ => F.random[UUID].map(_.toString))
          _    <- sqsClient.sendStringList(msgs)
          ref  <- F.syncThrowable(new mutable.Stack[String]())
          _    <-
            sqsClient
              .stringStream(batchSize)
              .evalMap {
                m =>
                  for {
                    _ <- sqsClient.deleteMessages(m.map(_.meta))
                    _ <- F.syncThrowable(ref ++= m.map(_.body))
                  } yield ()
              }.takeWhile(_ => ref.size < msgs.size).compile.drain.timeoutFail("Failed to process messages in 1 minute")(2.minute)
          _ <- F.traverse(msgs)(m => assertIO(ref.toSet.contains(m)))
        } yield ()
    }

    "Virtual queues" in bound {
      ctx =>
        import ctx.*

        val testMessage1 = "test message 1"
        val testMessage2 = "test message 2"

        val virtualQueue1 = VirtualQueue("virtual_1")
        val virtualQueue2 = VirtualQueue("virtual_2")
        for {

          _ <- sqsClient.sendString("test")

          _ <- sqsClient.sendStringFor(virtualQueue1, testMessage1)
          _ <- sqsClient.sendStringFor(virtualQueue2, testMessage2)

          res <- sqsClient.pollStringFor(virtualQueue1, 5)
          _   <- assertIO(res.length == 1)
          _   <- assertIO(res.headOption.exists(_.body == testMessage1))

          // adding deleteOrphanedAfter to test if other messages is deleted
          res2 <- sqsClient.pollStringFor(virtualQueue2, 5, deleteOrphanedAfter = Some(1.millis))
          _    <- assertIO(res2.length == 1)
          _    <- assertIO(res2.headOption.exists(_.body == testMessage2))

          // should delete all unknown messages
          res3 <- sqsClient.pollString(5)
          _    <- assertIO(res3.isEmpty)
        } yield ()
    }

    "support default queue requests" in bound {
      ctx =>
        import ctx.*
        for {
          _ <- {
            for {
              promise <- F.mkPromise[Throwable, TestRes]
              _       <- sqsClient.request[TestReq, TestRes](TestReq("test"), 2.minutes).redeem(promise.fail, promise.succeed).fork
              fork    <- sqsClient.processRequests[TestReq, TestRes](10.millis)(r => F.pure(TestRes(r.data))).fork
              res     <- promise.await.guarantee(fork.interrupt)
              _       <- assertIO(res.data == "test")
            } yield ()
          }.timeoutFail("Failed to process messages in 1 minute")(2.minutes)
          res0 <- sqsClient.pollString(10)
          _    <- assertIO(res0.isEmpty)
        } yield ()
    }

    "support virtual queue requests" in bound {
      ctx =>
        import ctx.*
        val testMessage1 = "test message 1"
        val testMessage2 = "test message 2"
        for {
          promise1 <- F.mkPromise[Throwable, TestRes]
          promise2 <- F.mkPromise[Throwable, TestRes]

          virtualQueue3 = VirtualQueue("virtual_3")
          virtualQueue4 = VirtualQueue("virtual_4")

          _ <- sqsClient.requestFor[TestReq, TestRes](virtualQueue3, TestReq(testMessage1), 2.minutes).redeem(promise1.fail, promise1.succeed).fork
          _ <- sqsClient.requestFor[TestReq, TestRes](virtualQueue4, TestReq(testMessage2), 2.minutes).redeem(promise2.fail, promise2.succeed).fork

          fork1 <- sqsClient.processRequestsFor[TestReq, TestRes](virtualQueue3, 10.millis)(req => assertIO(req.data == testMessage1).as(TestRes("1"))).fork
          fork2 <- sqsClient.processRequestsFor[TestReq, TestRes](virtualQueue4, 10.millis)(req => assertIO(req.data == testMessage2).as(TestRes("2"))).fork

          res1 <- promise1.await.guarantee(fork1.interrupt)
          _    <- assertIO(res1.data == "1")

          res2 <- promise2.await.guarantee(fork2.interrupt)
          _    <- assertIO(res2.data == "2")
        } yield ()
    }

    "support concurrent access to the requester/responder" in bound {
      ctx =>
        import ctx.*
        val parallelLevel = 3
        F.parTraverse(1 to parallelLevel) {
          i =>
            val testMessage = s"test message $i"
            val childName   = VirtualQueue(s"virtual_parallel_$i")
            val data        = i.toString
            for {
              promise <- F.mkPromise[Throwable, TestRes]
              _       <- sqsClient.requestFor[TestReq, TestRes](childName, TestReq(testMessage), 2.minutes).redeem(promise.fail, promise.succeed).fork
              fork    <- sqsClient.processRequestsFor[TestReq, TestRes](childName, 1.seconds)(req => assertIO(req.data == testMessage).as(TestRes(data))).fork
              res1    <- promise.await.guarantee(fork.interrupt)
              _       <- assertIO(res1.data == data)
            } yield ()
        }
    }
  }

  override protected def parallelEnvExecution: TestConfig.Parallelism   = TestConfig.Parallelism.Sequential
  override protected def parallelSuiteExecution: TestConfig.Parallelism = TestConfig.Parallelism.Sequential
  override protected def parallelTestExecution: TestConfig.Parallelism  = TestConfig.Parallelism.Sequential
}
