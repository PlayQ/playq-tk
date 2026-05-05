package d4s.query.models

import d4s.exceptions.DynamoException
import d4s.exceptions.DynamoException.InterpreterException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithPages.maybeTableName
import d4s.query.models.WithPagesOffset.IsWithPagesOffset
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.Select

import scala.annotation.unused

trait WithPagesOffset[DQ[+d] <: WithPagesOffset[DQ, PageKey, Rsp0, d], PageKey, Rsp0, +Dec]
  extends WithPages[DQ, PageKey, Dec]
  with WithSelect[DQ[Dec]]
  with WithPagesSize[DQ[Dec]]
  with WithProjectionExpression[DQ[Dec]]
  with IsWithPagesOffset {
  self: DQ[Dec] =>

  override type Rsp = Rsp0

  def extractScannedItemsCount(response: Rsp): Int

  final def countOnly: DQ[Int]        = withProjectionExpression(_ => None).withSelect(Select.COUNT).decode(extractItemsCount)
  final def withCount: DQ[(Dec, Int)] = decodeWith((a, c) => (c, extractItemsCount(a)))

  final def scannedCountOnly: DQ[Int]        = withProjectionExpression(_ => None).withSelect(Select.COUNT).decode(extractScannedItemsCount)
  final def withScannedCount: DQ[(Dec, Int)] = decodeWith((a, c) => (c, extractScannedItemsCount(a)))

  private[query] final def readPagedOffset[A](
    offset: Int,
    limit: Option[Int],
  )(asList: Dec => Iterable[A]
  ): DynamoQuery.Raw[List[A], List[A]] = DynamoQuery.Raw[List[A], List[A]](
    decoder     = QueryDecoder.pure,
    interpreter = WithPagesOffset.PagedOffsetInterpreter[DQ, PageKey, Rsp0, DynamoQuery.Raw[List[A], +_], Dec, A](self, offset, limit, asList),
  )
}

object WithPagesOffset {
  sealed trait IsWithPagesOffset

  implicit final class OffsetPagedReader[DQ[+d] <: WithPagesOffset[DQ, PageKey, Rsp, d], PageKey, Rsp, Dec](
    private val dynamoQuery: WithPagesOffset[DQ, PageKey, Rsp, Dec]
  ) {
    @inline def countAll: DynamoQuery.Raw[List[Int], Int]                                       = dynamoQuery.countOnly.readPaged(None)(List.apply(_)).map(_.sum)
    @inline def readOffset(offset: Int): DynamoQuery.Raw[List[Dec], List[Dec]]                  = dynamoQuery.readPagedOffset(offset, None)(List.apply(_))
    @inline def readOffsetLimit(offset: Int, limit: Int): DynamoQuery.Raw[List[Dec], List[Dec]] = dynamoQuery.readPagedOffset(offset, Some(limit))(List.apply(_))
  }

  implicit final class OffsetPagedReaderFlatten[DQ[+d] <: WithPagesOffset[DQ, PageKey, Rsp, d], Dec[_], PageKey, Rsp, A](
    private val dynamoQuery: WithPagesOffset[DQ, PageKey, Rsp, Dec[A]]
  )(implicit ev: Dec[A] <:< Iterable[A]
  ) {
    @inline def countAll: DynamoQuery.Raw[List[Int], Int]                                   = dynamoQuery.countOnly.readPaged(None)(List.apply(_)).map(_.sum)
    @inline def readOffset(offset: Int): DynamoQuery.Raw[List[A], List[A]]                  = dynamoQuery.readPagedOffset(offset, None)(ev.apply)
    @inline def readOffsetLimit(offset: Int, limit: Int): DynamoQuery.Raw[List[A], List[A]] = dynamoQuery.readPagedOffset(offset, Some(limit))(ev.apply)
  }

  private final case class PagedOffsetInterpreter[DQ0[+d] <: WithPagesOffset[DQ0, PageKey, Rsp0, d], PageKey, Rsp0, DQ1[+_], Dec, A](
    origin: DQ0[Dec],
    offset: Int,
    limit: Option[Int],
    asList: Dec => Iterable[A],
  ) extends QueryInterpreter[DQ1, List[A]] {
    @inline override def interpret[F[+_, +_]: IO2: Temporal2, Dec0](ctx: QueryCtx[F])(@unused ignoredQuery: DQ1[Dec0]): F[DynamoException, List[A]] = {
      for {
        _ <- F.when(limit.exists(_ < 0)) {
          F.fail {
            InterpreterException("PagedQuery", maybeTableName(origin), new IllegalArgumentException(s"Negative limit passed when executing paged read. Query: $origin."))
          }
        }
        _ <- F.when(offset < 0) {
          F.fail {
            InterpreterException("PagedQuery", maybeTableName(origin), new IllegalArgumentException(s"Negative offset passed when executing paged read. Query: $origin."))
          }
        }

        res <- F.ifThenElse(offset > 0)(
          readOffsetUnsafe(ctx),
          origin.readPaged[A](limit)(asList).interpret[F](ctx),
        )
      } yield res
    }

    @inline private def readOffsetUnsafe[F[+_, +_]: IO2: Temporal2](ctx: QueryCtx[F]): F[DynamoException, List[A]] = {
      // read paged counts until offset, then extract last page key and read paged until limit is reached
      origin
        .withSelect(Select.COUNT).withProjectionExpression(_ => None)
        .decode(identity).readPaged(Some(offset))(List(_))
        .interpret(ctx).flatMap {
          rsp =>
            rsp.lastOption.map(origin.extractPageKey) match {
              // we've reached offset mark and should continue reading with limit
              case Some(Some(lastKey)) => origin.withPageKey(Some(lastKey)).readPaged[A](limit)(asList).interpret[F](ctx)
              // whole table has been read, and there is nothing left to query
              case Some(None) => F.pure(Nil)
              // we've got no responses (impossible case)
              case None => F.pure(Nil)
            }
        }
    }
  }
}
