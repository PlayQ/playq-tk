package d4s.exceptions

import scala.annotation.tailrec

abstract class DynamoException(val message: String, val cause: Throwable) extends RuntimeException(message, cause) {
  require(cause ne null)
}

object DynamoException {
  @tailrec def unapply(arg: DynamoException): Some[(String, Throwable)] = arg.cause match {
    case inner: DynamoException => unapply(inner)
    case otherCause             => Some((arg.message, otherCause))
  }

  object shallow {
    def unapply(arg: DynamoException): Some[(String, Throwable)] = Some((arg.message, arg.cause))
  }

  final case class InterpreterException(operation: String, tableNames: List[String], override val cause: Throwable)
    extends DynamoException(s"Got error during executing `$operation`${InterpreterException.formatTableNames(tableNames)}. Cause: ${cause.getMessage}", cause)
  object InterpreterException {
    def formatTableNames(tableNames: List[String]): String = {
      tableNames match {
        case Nil              => ""
        case tableName :: Nil => s" for table `$tableName`"
        case tableNames       => s" for tables ${tableNames.mkString("`", "`, `", "`")}"
      }
    }
  }

  final class QueryException private (override val message: String, override val cause: Throwable) extends DynamoException(message, cause)
  object QueryException {
    def apply(cause: Throwable): QueryException = cause match {
      case q: QueryException => q
      case _                 => from(None, cause)
    }
    def apply(queryName: String, cause: Throwable): QueryException = from(Some(queryName), cause)

    private def from(queryName: Option[String], cause: Throwable): QueryException = {
      val mbName = queryName.fold(" ")(n => s" `$n` ")
      cause match {
        case DynamoException(message, cause) => new QueryException(s"Dynamo query${mbName}failed due to error: $message", cause)
        case cause                           => new QueryException(s"Dynamo query${mbName}failed due to error: ${cause.getMessage}", cause)
      }
    }
  }

  final case class DecoderException(override val message: String, maybeCause: Option[Throwable])
    extends DynamoException(message, maybeCause.getOrElse(new RuntimeException(message))) {

    def union(that: DecoderException): DecoderException = {
      val errorLog = message + "\n" + that.getMessage
      DecoderException(errorLog, maybeCause.orElse(that.maybeCause))
    }
  }
  object DecoderException {
    def apply(cause: Throwable): DecoderException = cause match {
      case d: DecoderException => d
      case _                   =>
        val mbMessage = Option(cause.getMessage).getOrElse("")
        DecoderException(s"Decoder failed due to error: $mbMessage", Some(cause))
    }
  }

}
