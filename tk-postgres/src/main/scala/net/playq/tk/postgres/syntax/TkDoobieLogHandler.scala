package net.playq.tk.postgres.syntax

import doobie.util.log
import doobie.util.log.{LogEvent, LogHandler, Parameters}
import izumi.functional.bio.{F, IO2}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.time.IzTime
import izumi.logstage.api.Log
import logstage.LogIO2

import java.time.ZonedDateTime
import java.util.UUID
import scala.collection.mutable
import scala.util.Try

trait TkDoobieLogHandler[F[+_, +_]] extends LogHandler[F[Throwable, _]]

object TkDoobieLogHandler {
  private final case class StringifyQuery(label: String, query: String, regexArguments: String)

  final class Impl[F[+_, +_]: IO2](logger: LogIO2[F]) extends TkDoobieLogHandler[F] {
    override def run(event: LogEvent): F[Throwable, Unit] = {
      lazy val query = stringifyQuery(event)
      event match {
        case _: log.Success =>
          F.when(logger.acceptable(CodePositionMaterializer().get, Log.Level.Debug)) {
            logger.debug(s"Executing query ${query.label}:\n${query.query}\n${query.regexArguments}")
          }
        case log.ProcessingFailure(_, _, _, _, _, failure) =>
          logger.warn(s"Query precessing failed ${query.label}:\n${query.query}\n${query.regexArguments}\n${failure.getMessage -> "Failure"}")
        case log.ExecFailure(_, _, _, _, failure) =>
          logger.warn(s"Query execution failed ${query.label}:\n${query.query}\n${query.regexArguments}\n${failure.getMessage -> "Failure"}")
      }
    }

    private[this] def stringifyQuery(event: LogEvent): StringifyQuery = {
      val stack  = mutable.Stack[(String, String)]()
      val params = event.params match {
        case Parameters.NonBatch(paramsAsList) => paramsAsList
        case Parameters.Batch(paramsAsLists)   => paramsAsLists().headOption.toList.flatten
      }
      val queryPreset = params.foldLeft(event.sql) {
        case (query, arg) =>
          val stringified = stringifyArg(arg)
          val adjusted    = if (stringified.contains('$') || stringified.contains('/')) {
            val newS = stringified.replace("$", "").replace("\\", "")
            stack.push((newS, stringified))
            newS
          } else {
            stringified
          }
          Try(query.replaceFirst("\\?", adjusted)).getOrElse(query)
      }
      StringifyQuery(event.label, queryPreset, stack.map { case (k, v) => s"$k->$v" }.mkString("; "))
    }

    private[this] def stringifyArg(arg: Any): String = {
      s"@arg[${arg match {
          case str: String         => s"'$str'"
          case time: ZonedDateTime => s"'${time.format(IzTime.ISO_LOCAL_DATE_TIME_3NANO)}'"
          case uuid: UUID          => s"'$uuid'"
          case _                   => arg.toString
        }}]"
    }
  }
}
