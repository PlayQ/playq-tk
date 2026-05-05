package d4s.query.models

import d4s.UnsafeDynamoOps
import d4s.exceptions.DynamoException
import d4s.exceptions.DynamoException.InterpreterException
import d4s.query.effect.*
import d4s.query.models.WithPages.IsWithPages
import d4s.query.{DynamoQuery, DynamoStream}
import fs2.Stream
import izumi.functional.bio.{F, IO2, Temporal2}

import scala.annotation.unused
import scala.collection.immutable.Queue

trait WithPages[DQ[+d] <: WithPages[DQ, PageKey, d], PageKey, +Dec]
  extends DynamoQuery[DQ, Dec]
  with WithPagesSize[DQ[Dec]]
  with WithPagesKey[PageKey, DQ[Dec]]
  with IsWithPages {
  self: DQ[Dec] =>

  def extractPageKey(rsp: Rsp): Option[PageKey]

  def extractItemsCount(rsp: Rsp): Int

  /**
    * Read single page from Dynamo.
    */
  private[query] final def readPage: DynamoQuery.Raw[Rsp, Dec] = DynamoQuery.Raw(
    decoder     = decoder,
    interpreter = new QueryInterpreter[DynamoQuery.Raw[Rsp, +_], Rsp] {
      override def interpret[F[+_, +_]: IO2: Temporal2, Dec1](ctx: QueryCtx[F])(@unused ignoredQuery: DynamoQuery.Raw[Rsp, Dec1]): F[DynamoException, Rsp] = {
        self.interpret(ctx)
      }
    },
  )

  /**
    * Read pages from Dynamo.
    * Result limit is completely handled by Dynamo, no trimming is performed.
    */
  private[query] final def readPaged[A](limit: Option[Int])(asList: Dec => Iterable[A]): DynamoQuery.Raw[List[A], List[A]] = DynamoQuery.Raw[List[A], List[A]](
    decoder     = QueryDecoder.pure,
    interpreter = WithPages.PagedInterpreter[DQ, PageKey, DynamoQuery.Raw[List[A], +_], Dec, A](self, limit, asList),
  )

  /**
    * Read pages from Dynamo as fs2.Stream.
    */
  private[query] final def readStream[A](asList: Dec => Iterable[A]): DynamoStream.Raw[A, A] = {
    DynamoStream.Raw[A, A](
      decoder = QueryDecoder.pure,
      stream  = WithPages.PagedStream[DQ, PageKey, DynamoStream.Raw[A, +_], Dec, A](self, asList),
    )
  }
}

object WithPages {
  sealed trait IsWithPages

  implicit final class PagedReader[DQ[+d] <: WithPages[DQ, PageKey, d], PageKey, Dec](
    private val dynamoQuery: WithPages[DQ, PageKey, Dec]
  ) {
    @inline def readAll: DynamoQuery.Raw[List[Dec], List[Dec]]               = dynamoQuery.readPaged(None)(List.apply(_))
    @inline def readLimit(limit: Int): DynamoQuery.Raw[List[Dec], List[Dec]] = dynamoQuery.readPaged(Some(limit))(List.apply(_))
    @inline def stream: DynamoStream.Raw[Dec, Dec]                           = dynamoQuery.readStream(List.apply(_))

    /** Unsafe to use, as Dynamo might return empty page if scan started from the wrong partition.
      * @see https://stackoverflow.com/questions/73618030/dynamodb-scan-with-exclusivestartkey-returns-zero-items-on-first-run-then-more-t
      */
    @inline def readPage(implicit @unused ev: UnsafeDynamoOps): DynamoQuery.Raw[?, Dec] = dynamoQuery.readPage
  }

  implicit final class PagedReaderFlatten[DQ[+d] <: WithPages[DQ, PageKey, d], PageKey, Dec[_], A](
    private val dynamoQuery: WithPages[DQ, PageKey, Dec[A]]
  )(implicit ev: Dec[A] <:< Iterable[A]
  ) {
    @inline def readAll: DynamoQuery.Raw[List[A], List[A]]               = dynamoQuery.readPaged(None)(ev.apply)
    @inline def readLimit(limit: Int): DynamoQuery.Raw[List[A], List[A]] = dynamoQuery.readPaged(Some(limit))(ev.apply)
    @inline def stream: DynamoStream.Raw[A, A]                           = dynamoQuery.readStream(ev.apply)

    /** Unsafe to use, as Dynamo might return empty page if scan started from the wrong partition.
      * @see https://stackoverflow.com/questions/73618030/dynamodb-scan-with-exclusivestartkey-returns-zero-items-on-first-run-then-more-t
      */
    @inline def readPage(implicit @unused ev: UnsafeDynamoOps): DynamoQuery.Raw[?, Dec[A]] = dynamoQuery.readPage
  }

  private final case class PagedInterpreter[DQ0[+d] <: WithPages[DQ0, PageKey, d], PageKey, DQ1[+_], Dec, A](
    origin: DQ0[Dec],
    limit: Option[Int],
    asList: Dec => Iterable[A],
  ) extends QueryInterpreter[DQ1, List[A]] {
    @inline override def interpret[F[+_, +_]: IO2: Temporal2, Dec0](ctx: QueryCtx[F])(@unused ignoredQuery: DQ1[Dec0]): F[DynamoException, List[A]] = {
      for {
        _ <- F.when(limit.exists(_ < 0)) {
          F.fail {
            InterpreterException("PagedQuery", maybeTableName(origin), new IllegalArgumentException(s"Negative limit passed when executing paged read. Query: $origin"))
          }
        }
        nonZeroLimit = limit.filter(_ > 0)
        res         <- go(ctx, nonZeroLimit)(None, 0, Queue.empty)
      } yield res
    }

    @inline private def go[F[+_, +_]: IO2: Temporal2](
      ctx: QueryCtx[F],
      limit: Option[Int],
    )(lastPage: Option[Option[PageKey]],
      count: Int,
      rows: Queue[A],
    ): F[DynamoException, List[A]] = {
      def stop: F[Nothing, List[A]] = {
        // Truncating here is not performed (`rows.take(limit)`) as Dynamo might return multiple pages with zero count
        // and if client wants to scan/query table without response decoding we should return all responses.
        // (trimming here might discard request responses)
        // Note: You shouldn't have to use `.take(limit)` anyway, because Dynamo will handle limit here via .withPageSize
        F.pure(rows.toList)
      }

      lastPage match {
        // we've read until limit
        case _ if limit.exists(_ <= count) => stop
        // we've read the whole table
        case Some(None) => stop
        // it's a first request in a loop, or we have page key to set
        case other =>
          // set page size to limit response with only needed entities (ignoring page size set by the user)
          val withPageSize = limit.fold(origin)(limit => origin.withPageSize(limit - count)(using d4s.unsafe.unsafeDynamoOps))
          // set page key from the latest response if we have one
          val withPageKey = other.fold(withPageSize)(withPageSize.withPageKey)
          (for {
            rsp          <- withPageKey.interpret(ctx).attempt
            dec          <- withPageKey.decoder.decode(rsp).map(asList)
            readCount     = rsp.toOption.fold(0)(withPageKey.extractItemsCount) + count
            maybeLastPage = rsp.toOption.flatMap(withPageKey.extractPageKey)
          } yield go(ctx, limit)(Some(maybeLastPage), readCount, rows.appendedAll(dec))).flatMap(identity)
      }
    }
  }

  private final case class PagedStream[DQ0[+d] <: WithPages[DQ0, PageKey, d], PageKey, DQ1[+_], Dec, A](
    origin: DQ0[Dec],
    asList: Dec => Iterable[A],
  ) extends QueryStream[DQ1, A] {
    @inline override def stream[F[+_, +_]: IO2: Temporal2, Dec0](
      ctx: QueryCtx[F],
      logger: QueryStreamLogger[F],
    )(@unused ignoredQuery: DQ1[Dec0]
    ): Stream[F[DynamoException, _], A] = {
      Stream
        .unfoldLoopEval[F[DynamoException, _], Option[PageKey], Iterable[A]](None) {
          lastPage =>
            logger.wrap(ctx) {
              // set page key from the latest response if we have one
              val withPageKey = lastPage.fold(origin)(k => origin.withPageKey(Some(k)))
              for {
                rsp          <- withPageKey.interpret(ctx).attempt
                dec          <- withPageKey.decoder.decode(rsp).map(asList)
                maybeLastPage = rsp.toOption.flatMap(withPageKey.extractPageKey)
              } yield (dec, maybeLastPage.map(Some(_)))
            }
        }.flatMap(res => Stream.emits(res.toSeq))
    }
  }

  private[models] def maybeTableName[DQ[+_], Dec](origin: DynamoQuery[DQ, Dec]): List[String] = origin match {
    case w: WithTableReference.Generic[?] => List(w.tableRef.tableName)
    case _                                => Nil
  }
}
