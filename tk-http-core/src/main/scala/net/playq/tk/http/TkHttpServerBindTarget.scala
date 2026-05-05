package net.playq.tk.http

import cats.Foldable
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{Async2, F, IO2, Temporal2}
import izumi.fundamentals.platform.files.FileLockMutex
import logstage.LogIO2
import net.playq.tk.http.config.HttpInterfaceConfig
import org.http4s.Uri

import java.net.*
import scala.concurrent.duration.DurationInt

final case class TkHttpServerBindTarget(
  bindingHost: String,
  reachableAt: InetAddress,
  port: Int,
) {
  private lazy val hostPortString   = s"${reachableAt.getHostAddress}:$port"
  def urlFor(proto: String): String = s"$proto://$hostPortString"

  lazy val httpUri: Uri  = Uri.unsafeFromString(urlFor("http"))
  lazy val httpsUri: Uri = Uri.unsafeFromString(urlFor("https"))
  lazy val wsUri: Uri    = Uri.unsafeFromString(urlFor("ws"))
  lazy val wssUri: Uri   = Uri.unsafeFromString(urlFor("wss"))

  override def toString: String = s"{ Listening: $bindingHost:$port, Available: http://$hostPortString }"
}

class TkHttpServerBindTargetProvider(interface: TkHttpServerBindTarget, socket: ServerSocket) {
  // TODO: all the code which references this AND manually constructs context is badly designed and must be rewritten
  def unsafeGet: TkHttpServerBindTarget = interface

  def unlockAndGet[F[+_, +_]: IO2]: F[Nothing, TkHttpServerBindTarget] = {
    for {
      _ <- F.syncThrowable(socket.close()).catchAll(_ => F.unit)
    } yield {
      interface
    }
  }
}

object TkHttpServerBindTargetProvider {
  final class Resource[F[+_, +_]: Async2: Temporal2](
    config: HttpInterfaceConfig,
    logger: LogIO2[F],
  ) extends Lifecycle.Of[F[Throwable, _], TkHttpServerBindTargetProvider]({

      def detectDefaultInterfaceAddress(): F[Throwable, InetAddress] = {
        config.defaultInterfaceAddressOverride match {
          case Some("!auto") | None =>
            F.syncThrowable(new Socket()).bracketAuto {
              socket =>
                F.syncThrowable {
                  // We use well-known DNS resolver address which runs a web server on port 80. By using IP address we avoid a DNS lookup
                  val testCounterparty = new InetSocketAddress(InetAddress.getByName("1.1.1.1"), 80)
                  socket.connect(testCounterparty)
                  socket.getLocalAddress
                }
            }.tapError {
              err => logger.warn(s"HTTP: was not able to identify service host address. $err")
            }

          case Some("!local") =>
            F.syncThrowable(InetAddress.getLocalHost)

          case Some(value) =>
            F.syncThrowable(InetAddress.getByName(value))
        }
      }

      def findAndLockPort(interface: InetAddress): F[Throwable, (ServerSocket, () => F[Throwable, Unit])] = F.suspend {
        import izumi.functional.bio.catz.BIOToMonad

        val shuffledList = config.portRange.toShuffledList
        Foldable[List]
          .collectFirstSomeM(shuffledList) {
            port =>
              // FileLockMutex prevents a concurrent `findAndLock` from acquiring the socket after `unlockAndGet`, but before http4s has bound socket
              val filename         = s"TkHttpServerBindTargetProvider-${interface.getHostAddress}-$port".replaceAll("[^a-zA-Z0-9._-]", "_")
              val fileLockResource = FileLockMutex.allocate[F[Throwable, _]](
                filename              = filename,
                retryWait             = 100.millis,
                maxAttempts           = 10,
                attemptLog            = (num, maxAttempts) => logger.debug(s"findAndLockPort: Attempt $num out of $maxAttempts to acquire file lock for $filename."),
                failLog               = attempts => logger.warn(s"findAndLockPort: Cannot acquire file lock for $filename after $attempts."),
                lockAlreadyExistedLog = logger.debug(s"findAndLockPort: File lock already existed for $filename"),
              )
              fileLockResource.unsafeAllocate().flatMap {
                case (maybeLock, closeFileLock) =>
                  maybeLock match {
                    case None    => F.pure(None)
                    case Some(_) =>
                      F.syncThrowable {
                        val boundSocket = new ServerSocket(port, 50, interface)
                        Option((boundSocket, closeFileLock))
                      }.catchAll {
                        err =>
                          logger
                            .warn(s"${port -> "Server Port"} is not available on interface $interface will try the other one. ${err.getMessage -> "Error"}")
                            .as(None)
                      }
                  }
              }
          }.fromOption(new BindException(s"Can not bind HTTP service: No available ports on interface $interface. Ports: ${shuffledList.mkString(", ")}."))
      }

      Lifecycle.suspend(for {
        listenOn    <- F.syncThrowable(InetAddress.getByName(config.host))
        reachableAt <-
          if (listenOn.isAnyLocalAddress) {
            detectDefaultInterfaceAddress()
          } else {
            F.pure(listenOn)
          }
        (socket, closeFileLock) <- findAndLockPort(listenOn)
        bindTarget              <- F.pure(
          TkHttpServerBindTarget(
            bindingHost = config.host,
            port        = socket.getLocalPort,
            reachableAt = reachableAt,
          )
        )
        _ <- logger.info(s"HTTP server $config produced ${bindTarget -> "bind target"}")

      } yield {
        // found port is locked after `findAndLock` above to prevent another service from assuming it
        // before TkHttpInterface is used - use `.unlockPort` immediately before using the port.
        Lifecycle.make(F.syncThrowable(new TkHttpServerBindTargetProvider(bindTarget, socket)))(_ => closeFileLock())
      })
    })
}
