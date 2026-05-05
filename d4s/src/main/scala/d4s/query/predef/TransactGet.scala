package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.{WithActions, WithCheckConditionSuccess}
import d4s.query.predef.transactions.TransactGetItemAction
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{TransactGetItemsRequest, TransactGetItemsResponse}

import scala.jdk.CollectionConverters.*

final case class TransactGet[+Dec](
  interpreter: QueryInterpreter[TransactGet, List[TransactGetItemsResponse]],
  decoder: QueryDecoder[List[TransactGetItemsResponse], Dec],
  actions: List[TransactGetItemAction] = Nil,
) extends DynamoQuery[TransactGet, Dec]
  with WithActions[TransactGetItemAction, TransactGet[Dec]]
  with WithCheckConditionSuccess[TransactGet, Dec] {

  override type Rsp = List[TransactGetItemsResponse]

  override def withInterpreter(f: QueryInterpreter[TransactGet, List[TransactGetItemsResponse]]): TransactGet[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[List[TransactGetItemsResponse], C1]): TransactGet[C1] =
    copy(decoder = f)

  /** Overwrites action list, subsequent calls do not append. */
  override def withActions(actions: List[TransactGetItemAction]): TransactGet[Dec] =
    copy(actions = actions)
}

object TransactGet {
  def apply(): TransactGet[List[TransactGetItemsResponse]] = {
    new TransactGet(interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[TransactGet, List[TransactGetItemsResponse]] = new QueryInterpreter[TransactGet, List[TransactGetItemsResponse]] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: TransactGet[Dec]): F[DynamoException, List[TransactGetItemsResponse]] = {
      ctx.execute("TransactGetItemBatch") {
        client =>
          val batches = query.actions.map(_.transactGetItem).grouped(ctx.transactConfig.getTransactSize).toList
          F.traverse(batches) {
            requests =>
              val rq = TransactGetItemsRequest.builder().transactItems(requests.asJava)
              client.executeFuture(_.transactGetItems(rq.build()))
          }
      }
    }
  }
}
