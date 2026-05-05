package net.playq.tk.control

import izumi.functional.bio.Exit.{Error, Interruption, Termination}
import izumi.functional.bio.{F, Panic2}
import logstage.LogIO2

/** Return type for methods that never return */
sealed trait Forever

object Forever {
  def loopForeverWithLog[F[+_, +_]: Panic2](
    loopName: String
  )(f: F[Any, Unit]
  )(implicit log: LogIO2[F]
  ): F[Nothing, Forever] = {
    logEffect(loopName)(f).forever
  }

  def loopForeverWithSignal[F[+_, +_]: Panic2](
    loopName: String,
    signal: InterruptSignal[F],
  )(f: F[Any, Unit]
  )(implicit log: LogIO2[F]
  ): F[Nothing, Unit] = {
    (for {
      finished <- signal.isInterrupted
      _        <- F.when(!finished)(logEffect(loopName)(f))
      next      = F.when(!finished)(loopForeverWithSignal(loopName, signal)(f))
    } yield next).flatMap(identity)
  }

  private def logEffect[F[+_, +_]: Panic2](loopName: String)(f: F[Any, Unit])(implicit log: LogIO2[F]): F[Nothing, Unit] = {
    f.guaranteeOnFailure {
      case Interruption(defect, _, trace) =>
        // note: will log external interruption in DEBUG, but the loop will be stopped (interruption is unrecoverable)
        log.debug(s"VERY FATAL $loopName ERROR: interrupted, STOPPING the loop, cannot be restarted after interrupt!!! $defect $trace")
      case Termination(defect, _, trace) =>
        log.crit(s"VERY FATAL $loopName ERROR: got defects, restarting the loop $defect $trace")
      case Error(exception, trace) =>
        log.error(s"FATAL $loopName ERROR: got exception, restarting the loop $exception $trace")
    }.sandboxExit.void
  }
}
