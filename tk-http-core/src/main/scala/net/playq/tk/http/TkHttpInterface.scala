package net.playq.tk.http

import cats.Foldable
import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{F, IO2}
import logstage.LogIO2
import net.playq.tk.http.config.HttpInterfaceConfig

import java.net.*

trait TkHttpInterface {
  def bindingHost: String
  def externalAddress: InetAddress
  def port: Int
  private[http] def unlockPort: Boolean
  override def toString: String = s"http://$bindingHost:$port, external address: ${externalAddress.getHostAddress}"
}

object TkHttpInterface {
  final case class Static(bindingHost: String, externalAddress: InetAddress, port: Int) extends TkHttpInterface {
    override private[http] def unlockPort: Boolean = true
  }

  final class Resource[F[+_, +_]: IO2](
    cfg: HttpInterfaceConfig,
    logger: LogIO2[F],
  ) extends Lifecycle.Of[F[Throwable, _], TkHttpInterface]({
      def identifyExternalAddress: Lifecycle[F[Throwable, _], InetAddress] = Lifecycle.liftF[F[Throwable, _], InetAddress] {
        F.syncThrowable(new Socket()).bracketAuto {
            socket =>
              F.syncThrowable {
                socket.connect(new InetSocketAddress("google.com", 80))
                socket.getLocalAddress
              }
          }.tapError {
            err => logger.warn(s"HTTP: was not able to identify service host address. $err")
          }
      }

      def findPort: Lifecycle[F[Throwable, _], ServerSocket] = {
        import izumi.functional.bio.catz.*
        val shuffledList = cfg.portRange.toShuffledList
        Lifecycle.liftF {
          Foldable[List]
            .collectFirstSomeM(shuffledList) {
              maybePort =>
                F.syncThrowable(new ServerSocket(maybePort)).map(Option(_)).catchAll {
                  err =>
                    logger
                      .warn(s"${maybePort -> "Server Port"} is not available will try the other one. ${err.getMessage -> "Error"}")
                      .as(Option.empty[ServerSocket])
                }
            }.fromOption(new BindException(s"Can not bind HTTP service: No available ports. Ports: ${shuffledList.mkString(", ")}."))
        }
      }

      for {
        socket   <- findPort
        external <- identifyExternalAddress
      } yield {
        new TkHttpInterface {
          override def bindingHost: String          = cfg.host
          override def externalAddress: InetAddress = external
          override def port: Int                    = socket.getLocalPort
          override private[http] def unlockPort: Boolean = {
            try {
              socket.close()
              socket.isClosed
            } catch {
              case _: Throwable => false
            }
          }
        }
      }
    })
}
