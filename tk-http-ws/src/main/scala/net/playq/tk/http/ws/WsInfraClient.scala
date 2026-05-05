package net.playq.tk.http.ws

import fs2.Stream
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BlockingIO2, Clock2, F, Fork2, IO2, Monad2, Primitives2, PrimitivesM2, Ref2, RefM2, Semaphore2, Temporal2}
import izumi.functional.lifecycle.LifecycleAggregator
import izumi.reflect.Tag
import logstage.LogIO2
import net.playq.tk.control.fiber.FiberEffect
import net.playq.tk.control.{Forever, InterruptSignal}
import net.playq.tk.http.ws.WsInfraClient.{WsClientPoolConfig, WsClientPooled}
import net.playq.tk.http.ws.model.codec.{WsWireDecoder, WsWireEncoder}
import net.playq.tk.quantified.AsyncThrowable
import org.http4s.Uri

import java.time.OffsetDateTime
import scala.concurrent.duration.{DurationInt, FiniteDuration}

final class WsInfraClient[F[+_, +_]: IO2: Primitives2: Temporal2: AsyncThrowable](
  connector: WsInfraConnector[F],
  clock: Clock2[F],
  config: WsClientPoolConfig,
  lifecycleAggregator: LifecycleAggregator[F, Throwable],
  connections: RefM2[F, Map[Uri, WsClientPooled[F]]],
)(implicit log: LogIO2[F]
) {

  def activeConnections: F[Nothing, Int] = connections.get.map(_.size)

  def acquireConnection(uri: Uri): Lifecycle[F[Throwable, _], WsInfraConnection[F]] = {
    // make sure that `pooled.acquire` called once on each acquire
    Lifecycle.make {
      connections.modify {
        connectionsMap =>
          connectionsMap.get(uri) match {
            case Some(pooled) if pooled.isOpen =>
              pooled.acquire.as(pooled -> connectionsMap)
            case other =>
              for {
                _                   <- F.traverse(other)(_.forceRelease())
                acquired            <- lifecycleAggregator.acquireKey(connector.connect(uri))
                (socket, releaseKey) = acquired
                lastRequestedRef    <- clock.nowOffset().flatMap(F.mkRef)
                connectionCounter   <- F.mkSemaphore(Long.MaxValue)
                closeSocket          = {
                  lifecycleAggregator.release(releaseKey) *>
                  log.debug(s"WS Client pool: Connection closed $uri.")
                }
                pooled = new WsClientPooled(
                  wrapped           = socket,
                  clock             = clock,
                  lastRequestedRef  = lastRequestedRef,
                  connectionCounter = connectionCounter,
                  config            = config,
                )(closeSocket)
                _ <- pooled.acquire
                _ <- log.debug(s"WS Client pool: Connection created $uri")
              } yield pooled -> connectionsMap.updated(uri, pooled)
          }
      }
    }(_.release)
      .evalTap(_ => log.debug(s"WS Client pool: Connection acquired $uri"))
      .map(_.client)
  }

  def exchangeGeneric[Req: WsWireEncoder, Rsp: WsWireDecoder](uri: Uri, tag: String, req: Req, timeout: FiniteDuration = 5.minutes): F[Throwable, Rsp] = {
    acquireConnection(uri).use(_.exchangeGeneric[Req, Rsp](tag, req, timeout))
  }

  def exchange[Req: WsWireEncoder: Tag, Rsp: WsWireDecoder](uri: Uri, req: Req, timeout: FiniteDuration = 5.minutes): F[Throwable, Rsp] = {
    acquireConnection(uri).use(_.exchange[Req, Rsp](req, timeout))
  }

  def subscribeGeneric[Req: WsWireEncoder, Rsp: WsWireDecoder](uri: Uri, tag: String, req: Req): Stream[F[Throwable, _], Rsp] = {
    Stream.resource(acquireConnection(uri).toCats).flatMap(_.subscribeGeneric[Req, Rsp](tag, req))
  }

  def subscribe[Req: WsWireEncoder: Tag, Rsp: WsWireDecoder](uri: Uri, message: Req): Stream[F[Throwable, _], Rsp] = {
    Stream.resource(acquireConnection(uri).toCats).flatMap(_.subscribe[Req, Rsp](message))
  }

  def releaseAll(): F[Throwable, Unit] = {
    connections.update_ {
      F.traverse_(_) { case (_, c) => c.forceRelease() }.as(Map.empty)
    }
  }
}

object WsInfraClient {
  final case class WsClientPoolConfig(
    idleTimeout: FiniteDuration
  )

  final class WsClientPooled[F[+_, +_]: Monad2: Temporal2: AsyncThrowable](
    wrapped: WsInfraConnection[F],
    clock: Clock2[F],
    lastRequestedRef: Ref2[F, OffsetDateTime],
    connectionCounter: Semaphore2[F],
    config: WsClientPoolConfig,
  )(closeSocket: => F[Throwable, Unit]
  ) {

    def client: WsInfraConnection[F] = new WsInfraConnection[F] {
      override def isOpen: Boolean                                                                                               = wrapped.isOpen
      override def subscribeGeneric[Req: WsWireEncoder, Rsp: WsWireDecoder](tag: String, req: Req): Stream[F[Throwable, _], Rsp] = {
        interruptingWrapper(wrapped.subscribeGeneric[Req, Rsp](tag, req))
      }
      override def subscribe[Req: WsWireEncoder: Tag, Rsp: WsWireDecoder](req: Req): Stream[F[Throwable, _], Rsp] = {
        interruptingWrapper(wrapped.subscribe[Req, Rsp](req))
      }
    }

    def acquire: F[Nothing, Unit] = {
      for {
        _ <- connectionCounter.acquire
        _ <- connectionHeartbeat()
      } yield ()
    }

    def release: F[Nothing, Unit] = {
      for {
        _ <- connectionCounter.release
      } yield ()
    }

    /** Try to release connection if it's unused and idle timeout */
    def tryRelease(timeout: FiniteDuration = 50.millis): F[Throwable, Boolean] = {
      for {
        expiration    <- clock.nowOffset().map(_.minusSeconds(config.idleTimeout.toSeconds))
        lastRequested <- lastRequestedRef.get
        res           <- F.ifThenElse(lastRequested.isBefore(expiration))(
          connectionCounter.acquireN(Long.MaxValue).timeout(timeout).flatMap {
            case Some(_) => forceRelease().as(true)
            case None    => F.pure(false)
          },
          F.pure(false),
        )
      } yield res
    }

    def forceRelease(): F[Throwable, Unit] = F.unit *> closeSocket

    def isOpen: Boolean = client.isOpen

    private def connectionHeartbeat(): F[Nothing, Unit] = {
      clock.nowOffset().flatMap(lastRequestedRef.set)
    }

    /** Wrap connection stream with heartbeat. */
    private def interruptingWrapper[A](stream: => Stream[F[Throwable, _], A]): Stream[F[Throwable, _], A] = {
      stream.evalTap(_ => connectionHeartbeat())
    }
  }

  final class Resource[F[+_, +_]: IO2: Primitives2: PrimitivesM2: Temporal2: AsyncThrowable: Fork2: BlockingIO2](
    connector: WsInfraConnector[F],
    clock: Clock2[F],
    config: WsClientPoolConfig,
  )(implicit log: LogIO2[F]
  ) extends Lifecycle.Of(
      for {
        lifecycleAggregator <- LifecycleAggregator.make
        connections         <- Lifecycle.liftF(F.mkRefM(Map.empty[Uri, WsClientPooled[F]]))
        cleaner              = new WsClientsPoolCleaner(connections, config)
        _                   <- cleaner.lifecycle
        _                   <- Lifecycle.make[F[Throwable, _], Unit](F.unit) {
          _ =>
            connections.update_ {
              F.traverse_(_)(_._2.forceRelease()).as(Map.empty[Uri, WsClientPooled[F]])
            }
        }
      } yield new WsInfraClient(connector, clock, config, lifecycleAggregator, connections)
    )

  final class WsClientsPoolCleaner[F[+_, +_]: IO2: Temporal2](
    connections: RefM2[F, Map[Uri, WsClientPooled[F]]],
    config: WsClientPoolConfig,
  )(implicit log: LogIO2[F]
  ) extends FiberEffect[F] {
    override val fiberName: String = "WS Client Pool cleaner"

    override def runInFiber(interruptSignal: InterruptSignal[F]): F[Nothing, Any] = {
      Forever.loopForeverWithSignal(fiberName, interruptSignal) {
        for {
          _ <- cleanupConnections()
          _ <- F.sleep(config.idleTimeout)
        } yield ()
      }
    }

    private def cleanupConnections(): F[Throwable, Unit] = {
      connections.update_ {
        connectionsMap =>
          F.traverse(connectionsMap) {
            case (uri, client) if client.isOpen => client.tryRelease().map(isReleased => Option.when(!isReleased)(uri -> client))
            case (_, client)                    => client.forceRelease().as(None)
          }.map(_.flatten.toMap)
      }
    }
  }
}
