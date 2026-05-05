package d4s.query

import d4s.query.models.WithPages.IsWithPages
import d4s.query.models.WithPagesOffset.IsWithPagesOffset

import scala.annotation.implicitAmbiguous

/** Safeguard to check that query is properly constructed.
  *
  * Uses ambiguous implicits checks to fail compilation when
  * prohibited types are used in [[d4s.Dynamo.run]].
  *
  * Execution strategy should be specified to make sure that execution result
  * either contains a complete result set, or explicitly requests a single page.
  *
  * You can do this via one the following methods, depending on your query:
  *
  * - [[d4s.query.models.WithPages.PagedReader.readPage]]
  * - [[d4s.query.models.WithPages.PagedReader.readAll]]
  * - [[d4s.query.models.WithPages.PagedReader.readLimit]]
  * - [[d4s.query.models.WithPages.PagedReader.stream]]
  * - [[d4s.query.models.WithPagesOffset.OffsetPagedReader.readOffset]]
  * - [[d4s.query.models.WithPagesOffset.OffsetPagedReader.readOffsetLimit]]
  */
sealed abstract class IsSafeQuery[DQ[+_]]
object IsSafeQuery extends IsSafeQueryLowPriority1 {
  @implicitAmbiguous(
    "DynamoQuery ${DQ}, a subtype of WithPagesOffset, can not be executed here.\n" +
    "Please construct a valid query from ${DQ}.\n" +
    "For paged queries you must specify execution type using one of the following methods (or their flattenized variants):\n" +
    " - d4s.query.models.WithPages.PagedReader.readPage\n" +
    " - d4s.query.models.WithPages.PagedReader.readAll\n" +
    " - d4s.query.models.WithPages.PagedReader.readLimit\n" +
    " - d4s.query.models.WithPages.PagedReader.stream\n" +
    " - d4s.query.models.WithPagesOffset.OffsetPagedReader.readOffset\n" +
    " - d4s.query.models.WithPagesOffset.OffsetPagedReader.readOffsetLimit\n"
  )
  implicit def isPagedOffset1[DQ[+_] <: IsWithPagesOffset]: IsSafeQuery[DQ] = null
  implicit def isPagedOffset2[DQ[+_] <: IsWithPagesOffset]: IsSafeQuery[DQ] = null
}
sealed trait IsSafeQueryLowPriority1 extends IsSafeQueryLowPriority2 {
  @implicitAmbiguous(
    "DynamoQuery ${DQ}, a subtype of WithPages, can not be executed here.\n" +
    "Please construct a valid query from ${DQ}.\n" +
    "For paged queries you must specify execution type using one of the following methods (or their flattenized variants):\n" +
    " - d4s.query.models.WithPages.PagedReader.readPage\n" +
    " - d4s.query.models.WithPages.PagedReader.readAll\n" +
    " - d4s.query.models.WithPages.PagedReader.readLimit\n" +
    " - d4s.query.models.WithPages.PagedReader.stream\n"
  )
  implicit final def isPaged1[DQ[+_] <: IsWithPages]: IsSafeQuery[DQ] = null
  implicit final def isPaged2[DQ[+_] <: IsWithPages]: IsSafeQuery[DQ] = null
}
sealed trait IsSafeQueryLowPriority2 {
  /** Low priority implicit, without any type bounds. */
  implicit final def isSafeQuery[DQ[+_]]: IsSafeQuery[DQ] = null
}
