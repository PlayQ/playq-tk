package net.playq.tk.concurrent

import izumi.functional.bio.{Bracket2, Exit, Fiber2, Fork2}
import logstage.LogIO2

object utils {
  def forkAndLogExit[F[+_, +_]: Bracket2: Fork2, E, A](log: LogIO2[F], process: String)(f: F[E, A]): F[Nothing, Fiber2[F, E, A]] = {
    val logger = log("process" -> process)
    f.guaranteeCase {
      case Exit.Success(exitResult)               => logger.info(s"Exited with Success $exitResult")
      case Exit.Interruption(cause, stackTrace)   => logger.crit(s"Exited with Interruption $cause $stackTrace")
      case Exit.Error(cause, stackTrace)          => logger.error(s"Exited with Failure $cause $stackTrace")
      case Exit.Termination(cause, _, stackTrace) => logger.crit(s"Exited with Termination $cause $stackTrace}")
    }.fork
  }
}
