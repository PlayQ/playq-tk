package net.playq.tk.postgres.syntax

import doobie.util.log
import doobie.util.log.{LogEvent, LogHandler}
import izumi.fundamentals.platform.language.CodePositionMaterializer
import izumi.fundamentals.platform.time.IzTime
import izumi.logstage.api.Log.LoggerId
import izumi.logstage.api.{IzLogger, Log}

import java.time.ZonedDateTime
import java.util.UUID
import scala.collection.mutable
import scala.util.Try

final class TkDoobieLogHandler(
  logger: IzLogger
) {
  implicit val logHandler: LogHandler = LogHandler(handleLogEvent)

  private[this] def handleLogEvent(event: LogEvent): Unit = {
    lazy val query = stringifyQuery(event)
    event match {
      case _: log.Success =>
        if (logger.acceptable(LoggerId(CodePositionMaterializer().get.applicationPointId), Log.Level.Debug)) {
          logger.debug(s"Executing query:\n${query.query} \n${query.regexArguments}")
        }
      case log.ProcessingFailure(_, _, _, _, failure) =>
        logger.warn(s"Query precessing failed:\n${query.query} \n${query.regexArguments} \n${failure.getMessage -> "Failure"}")
      case log.ExecFailure(_, _, _, failure) =>
        logger.warn(s"Query execution failed:\n${query.query} \n${query.regexArguments} \n${failure.getMessage -> "Failure"}")
    }
  }

  private[this] def stringifyQuery(event: LogEvent): StringifyQuery = {
    val stack = mutable.Stack[(String, String)]()
    val queryPreset = event.args.foldLeft(event.sql) {
      case (query, arg) =>
        val stringified = stringifyArg(arg)
        val adjusted = if (stringified.contains('$') || stringified.contains('/')) {
          val newS = stringified.replace("$", "").replace("\\", "")
          stack.push((newS, stringified))
          newS
        } else {
          stringified
        }
        Try(query.replaceFirst("\\?", adjusted)).getOrElse(query)
    }
    new StringifyQuery {
      override val query: String          = queryPreset
      override val regexArguments: String = stack.map { case (k, v) => s"$k->$v" }.mkString("; ")
    }
  }

  private[this] def stringifyArg(arg: Any): String = {
    s"@arg[${arg match {
      case str: String         => s"'$str'"
      case time: ZonedDateTime => s"'${time.format(IzTime.ISO_LOCAL_DATE_TIME_3NANO)}'"
      case uuid: UUID          => s"'$uuid'"
      case _                   => arg.toString
    }}]"
  }

  trait StringifyQuery {
    def query: String
    def regexArguments: String
  }
}
