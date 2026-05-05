package d4s.query.effect

import d4s.exceptions.DynamoException
import d4s.exceptions.DynamoException.InterpreterException
import izumi.functional.bio.{Error2, F}
import logstage.LogIO2
import software.amazon.awssdk.services.dynamodb.model.*

import scala.reflect.ClassTag

trait QueryLogger {
  self =>
  def handler[F[+_, +_]: Error2](logger: LogIO2[F]): PartialFunction[Throwable, F[Nothing, Unit]]

  @inline final def withLogger[F[+_, +_]: Error2, E <: Throwable, A](logger: LogIO2[F])(f: F[E, A]): F[E, A] = {
    f.tapError(e => handler(logger).lift(e).getOrElse(F.unit))
  }

  final def ++(other: QueryLogger): QueryLogger    = merge(other)
  final def merge(other: QueryLogger): QueryLogger = new QueryLogger {
    override def handler[F[+_, +_]: Error2](logger: LogIO2[F]): PartialFunction[Throwable, F[Nothing, Unit]] = {
      other.handler(logger).orElse(self.handler(logger))
    }
  }
  final def ignoreErrors(predicate: PartialFunction[Throwable, Boolean]): QueryLogger = merge(QueryLogger.ignoring(predicate))
  final def ignoreError[ErrorType: ClassTag]: QueryLogger                             = ignoreErrors { case DynamoException(_, _: ErrorType) => true }
}

object QueryLogger {
  @inline def empty: QueryLogger = new QueryLogger {
    override def handler[F[+_, +_]: Error2](logger: LogIO2[F]): PartialFunction[Throwable, F[Nothing, Unit]] = {
      _ => F.unit
    }
  }

  @inline def ignoring(predicate: PartialFunction[Throwable, Boolean]): QueryLogger = new QueryLogger {
    override def handler[F[+_, +_]: Error2](logger: LogIO2[F]): PartialFunction[Throwable, F[Nothing, Unit]] = {
      case e if predicate.lift(e).getOrElse(false) => F.unit
    }
  }

  @inline def default: QueryLogger = new QueryLogger {
    override def handler[F[+_, +_]: Error2](logger: LogIO2[F]): PartialFunction[Throwable, F[Nothing, Unit]] = {
      case InterpreterException(operation, tableNames, failure) =>
        logger.error(s"Dynamo: Got error during executing $operation${InterpreterException.formatTableNames(tableNames)}. ${failure -> "Failure"}.")
    }
  }

  def ignoringTableNotFound: QueryLogger = ignoring {
    case DynamoException(_, _: ResourceNotFoundException | _: ResourceInUseException | _: TableNotFoundException) => true
  }

  def ignoringLimitExceed: QueryLogger = ignoring {
    case DynamoException(_, _: ContinuousBackupsUnavailableException) => true
    case DynamoException(_, e: LimitExceededException)                => e.getMessage.contains("tables can be created, updated, or deleted simultaneously")
  }
}
