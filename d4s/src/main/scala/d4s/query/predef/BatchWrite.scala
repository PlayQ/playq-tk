package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.QueryInterpreter.batchLoop
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.WithActions
import d4s.query.predef.batches.BatchWriteRequestAction
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{BatchWriteItemRequest, BatchWriteItemResponse, WriteRequest}

import scala.jdk.CollectionConverters.*

final case class BatchWrite[+Dec](
  interpreter: QueryInterpreter[BatchWrite, List[BatchWriteItemResponse]],
  decoder: QueryDecoder[List[BatchWriteItemResponse], Dec],
  actions: List[BatchWriteRequestAction],
) extends DynamoQuery[BatchWrite, Dec]
  with WithActions[BatchWriteRequestAction, BatchWrite[Dec]] {

  override type Rsp = List[BatchWriteItemResponse]

  override def withInterpreter(f: QueryInterpreter[BatchWrite, List[BatchWriteItemResponse]]): BatchWrite[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[List[BatchWriteItemResponse], C1]): BatchWrite[C1] =
    copy(decoder = f)

  /** Overwrites batch list, subsequent calls do not append. */
  override def withActions(actions: List[BatchWriteRequestAction]): BatchWrite[Dec] =
    copy(actions = actions)

  private def requestBuilder: BatchWriteItemRequest.Builder = {
    BatchWriteItemRequest.builder()
  }

  private def requestItems: Map[String, List[WriteRequest]] = {
    actions.groupMap(_.tableRef.tableName)(_.writeRequest)
  }
}

object BatchWrite {
  def apply(actions: List[BatchWriteRequestAction] = Nil): BatchWrite[List[BatchWriteItemResponse]] = {
    new BatchWrite(interpreter, QueryDecoder.pure, actions)
  }

  def interpreter: QueryInterpreter[BatchWrite, List[BatchWriteItemResponse]] = {
    BatchWrite.executeWriteBatch[BatchWrite]("BatchWrite")(
      requestBuilder = _.requestBuilder,
      requestItems   = _.requestItems,
    )
  }

  @inline def executeWriteBatch[DQ[+_]](
    operation: String
  )(requestBuilder: DQ[Any] => BatchWriteItemRequest.Builder,
    requestItems: DQ[Any] => Map[String, List[WriteRequest]],
  ): QueryInterpreter[DQ, List[BatchWriteItemResponse]] = new QueryInterpreter[DQ, List[BatchWriteItemResponse]] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DQ[Dec]): F[DynamoException, List[BatchWriteItemResponse]] = {
      val rqs        = requestItems(query)
      val tableNames = rqs.keysIterator.toList
      ctx.execute(operation, tableNames) {
        client =>
          val batches = rqs.toList.flatMap { case (t, rs) => rs.map(t -> _) }.grouped(ctx.batchConfig.writeBatchSize).toList
          F.traverse(batches) {
            requests =>
              batchLoop(
                rq0         = requestBuilder(query).requestItems(requests.groupBy(_._1).view.mapValues(_.map(_._2).asJava).toMap.asJava),
                batchConfig = ctx.batchConfig,
              )(interpret = rq => client.executeFuture(_.batchWriteItem(rq.build())))(
                getUnprocessedKeys = _.unprocessedItems()
              )(mkNewRq = _.requestItems(_))
          }
      }
    }
  }

}
