package d4s.query.effect

import d4s.config.{DynamoBatchConfig, DynamoConfig, DynamoTransactionConfig}
import d4s.exceptions.DynamoException.InterpreterException
import d4s.services.DynamoClient
import izumi.functional.bio.{Error2, Temporal2}
import logstage.LogIO2
import net.playq.tk.quantified.ConcurrentThrowable

final class QueryCtx[F[+_, +_]](
  dynamoClient: DynamoClient[F],
  val logger: LogIO2[F],
  val batchConfig: DynamoBatchConfig,
  val transactConfig: DynamoTransactionConfig,
  val dynamoConfig: DynamoConfig,
)(implicit
  val concurrentThrowable: ConcurrentThrowable[F]
) {
  def execute[A](operation: String, tableName: String)(f: DynamoClient[F] => F[Throwable, A])(implicit e: Error2[F]): F[InterpreterException, A] = {
    execute(operation, List(tableName))(f)
  }

  def execute[A](operation: String)(f: DynamoClient[F] => F[Throwable, A])(implicit e: Error2[F]): F[InterpreterException, A] = {
    execute(operation, Nil)(f)
  }

  def execute[A](operation: String, tables: List[String])(f: DynamoClient[F] => F[Throwable, A])(implicit e: Error2[F]): F[InterpreterException, A] = {
    f(dynamoClient).leftMap(InterpreterException(operation, tables, _))
  }
}
