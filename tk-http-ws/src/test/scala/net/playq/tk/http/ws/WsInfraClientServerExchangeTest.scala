package net.playq.tk.http.ws

import distage.{Id, ModuleDef}
import fs2.Stream
import izumi.distage.modules.DefaultModule2
import izumi.distage.testkit.model.TestConfig
import izumi.functional.bio.{Async2, F, Temporal2}
import izumi.idealingua.runtime.circe.IRTWithCirce
import izumi.reflect.TagKK
import logstage.LogIO2
import net.playq.tk.http.TkHttp4sServerResource.TkHttp4sServer
import net.playq.tk.http.TkHttpServerBindTarget
import net.playq.tk.http.config.HttpSecretConfig
import net.playq.tk.http.ws.WsInfraClientServerExchangeTest.*
import net.playq.tk.http.ws.model.codec.{WsCodecWithCirce, WsWireDecoder, WsWireEncoder}
import net.playq.tk.quantified.{AsyncThrowable, ConcurrentThrowable, TemporalThrowable}
import net.playq.tk.test.TkTestBaseCtx
import zio.IO

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

final class WsInfraClientServerExchangeTestProd extends WsInfraClientServerExchangeTest[IO] {
  override protected def dummyStorage: Boolean = true
}

object WsInfraClientServerExchangeTest {
  final case class Ctx[F[+_, +_]](
    client: WsInfraClient[F],
    infraWsServer: WsInfraServer[F],
    httpServer: TkHttp4sServer,
    serverBinding: TkHttpServerBindTarget,
    logger: LogIO2[F],
    globalExecutionContext: ExecutionContext @Id("global"),
  )(implicit
    val T: Temporal2[F],
    val S: ConcurrentThrowable[F],
    val A: AsyncThrowable[F],
  )

  final case class TestRequest(msg: String, count: Int)
  object TestRequest extends WsCodecWithCirce[TestRequest]

  final case class TestResponse(msg: String, num: Int)
  object TestResponse extends WsCodecWithCirce[TestResponse]

  final class TestMessageHandler[F[+_, +_]: TemporalThrowable] extends WsInfraMessageHandler.Tagged[F, TestRequest, TestResponse] {
    override def handle(message: TestRequest): Stream[F[Throwable, _], TestResponse] = {
      Stream
        .awakeEvery(50.millis).zipWithIndex
        .map { case (_, idx) => TestResponse(message.msg, idx.toInt) }
        .take(message.count.toLong)
    }
  }

  final case class ErrorTestRequest(msg: String)
  object ErrorTestRequest extends WsCodecWithCirce[ErrorTestRequest]

  final case class ErrorTestResponse(msg: String)
  object ErrorTestResponse extends WsCodecWithCirce[ErrorTestResponse]

  final class TestErrorMessageHandler[F[+_, +_]: AsyncThrowable] extends WsInfraMessageHandler.Tagged[F, ErrorTestRequest, ErrorTestResponse] {
    override def handle(message: ErrorTestRequest): Stream[F[Throwable, _], ErrorTestResponse] =
      Stream.raiseError[F[Throwable, _]](new Exception("Failed to handle message"))
  }

  final case class NoHandlerTestMessage(msg: String)
  object NoHandlerTestMessage extends WsCodecWithCirce[NoHandlerTestMessage]

  final case class CodecFailureTestMessage(msg: String)
  object CodecFailureTestMessage extends IRTWithCirce[CodecFailureTestMessage] {
    implicit val wireEncoder: WsWireEncoder[CodecFailureTestMessage] = (_: CodecFailureTestMessage) => throw new RuntimeException("Codec failed")
    implicit val wireDecoder: WsWireDecoder[CodecFailureTestMessage] = (_: Array[Byte]) => throw new RuntimeException("Codec failed")
  }
}

abstract class WsInfraClientServerExchangeTest[F[+_, +_]: Async2: TagKK: DefaultModule2] extends TkTestBaseCtx[F, Ctx[F]] {
  override def moduleOverrides: distage.Module = super.moduleOverrides ++ new ModuleDef {
    many[WsInfraMessageHandler[F, ?, ?]]
      .add[TestMessageHandler[F]]
      .add[TestErrorMessageHandler[F]]

    make[HttpSecretConfig].from(HttpSecretConfig("schema", "secret"))
  }

  "WsClient and WsServer" should {
    "exchange messages" in bound {
      ctx =>
        import ctx.*
        for {
          message1  <- F.sync(TestRequest(UUID.randomUUID().toString, 1))
          response1 <- client.exchange[TestRequest, TestResponse](serverBinding.wsUri, message1)
          _         <- assertIO(response1.msg == message1.msg)

          message2  <- F.sync(TestRequest(UUID.randomUUID().toString, 1))
          response2 <- client.exchange[TestRequest, TestResponse](serverBinding.wsUri, message2)
          _         <- assertIO(response2.msg == message2.msg)

          message3 <- F.sync(TestRequest(UUID.randomUUID().toString, 5))
          streamed <- client.subscribe[TestRequest, TestResponse](serverBinding.wsUri, message3).take(3).compile.toList
          _        <- assertIO(streamed.size == 3 && streamed.forall(_.msg == message3.msg))

          // parallel connections test
          _ <- client.acquireConnection(serverBinding.wsUri).use {
            connection =>
              F.parTraverseN(10)(1 to 10) {
                _ =>
                  for {
                    messageRnd <- F.sync(TestRequest(UUID.randomUUID().toString, 5))
                    streamed   <- connection.subscribe[TestRequest, TestResponse](messageRnd).compile.toList
                    _          <- assertIO(streamed.size == 5 && streamed.forall(_.msg == messageRnd.msg))
                  } yield ()
              }
          }
        } yield ()
    }

    "stop return stream if received errored message" in bound {
      ctx =>
        import ctx.*
        for {
          socket <- F.sync(client.acquireConnection(serverBinding.wsUri))
          _      <- socket.use[Unit] {
            socket =>
              for {
                response <- socket
                  .subscribe[TestRequest, TestResponse](TestRequest(UUID.randomUUID().toString, 5))
                  .compile.toList
                errorResponse <- socket
                  .subscribe[ErrorTestRequest, ErrorTestResponse](ErrorTestRequest(UUID.randomUUID().toString))
                  .handleErrorWith(_ => Stream.empty).compile.last
                _ <- assertIO(errorResponse.isEmpty)
                _ <- assertIO(response.nonEmpty)
              } yield ()
          }
        } yield ()
    }

    "response stream should fail with HandlerNotFound exception" in bound {
      ctx =>
        import ctx.*
        for {
          response <- client
            .subscribe[NoHandlerTestMessage, NoHandlerTestMessage](serverBinding.wsUri, NoHandlerTestMessage(UUID.randomUUID().toString))
            .map(_.msg).handleErrorWith(e => Stream.emit(e.toString)).compile.last
          _ <- assertIO(response.exists(_.contains("HandlerNotFound")))
          _ <- client.exchange[TestRequest, TestResponse](serverBinding.wsUri, TestRequest(UUID.randomUUID().toString, 1))
        } yield ()
    }

    "response stream should fail with CodecFailed exception" in bound {
      ctx =>
        import ctx.*
        for {
          response <- client
            .subscribe[CodecFailureTestMessage, CodecFailureTestMessage](serverBinding.wsUri, CodecFailureTestMessage(UUID.randomUUID().toString))
            .map(_.msg).handleErrorWith(e => Stream.emit(e.toString)).compile.last
          _ <- assertIO(response.exists(_.contains("CodecFailed")))
        } yield ()
    }

    "handle multiple parallel requests correctly" in bound {
      ctx =>
        import ctx.*
        for {
          _ <- F.parTraverseN(100)(1 to 1000) {
            _ =>
              val id = UUID.randomUUID().toString
              client
                .subscribe[TestRequest, TestResponse](serverBinding.wsUri, TestRequest(id, 10))
                .compile.count.flatMap(c => F.sync(assert(c == 10L, s"Failed to get complete stream for $id.")))
          }
          _ <- client.releaseAll()
        } yield ()
    }
  }

  override protected def parallelEnvExecution: TestConfig.Parallelism   = TestConfig.Parallelism.Sequential
  override protected def parallelSuiteExecution: TestConfig.Parallelism = TestConfig.Parallelism.Sequential
  override protected def parallelTestExecution: TestConfig.Parallelism  = TestConfig.Parallelism.Sequential
}
