package net.playq.tk.control

import izumi.functional.bio.Exit.{Error, Interruption, Termination}
import izumi.functional.bio.Panic2
import logstage.LogIO2

/** Return type for methods that never return */
sealed trait Forever

object Forever {
  def loopForeverWithLog[F[+_, +_]: Panic2](loopName: String)(f: F[Any, Unit])(implicit log: LogIO2[F]): F[Nothing, Forever] = {
    f.sandbox.catchAll {
      case Interruption(defect, trace) =>
        // note: external interruption will be caught by this.
        log.crit(s"VERY FATAL $loopName ERROR: interrupted, restarting the loop $defect $trace")
      case Termination(defect, _, trace) =>
        log.crit(s"VERY FATAL $loopName ERROR: got defects, restarting the loop $defect $trace")
      case Error(exception, trace) =>
        log.error(s"FATAL $loopName ERROR: got exception, restarting the loop $exception $trace")
    }.forever
  }
}
