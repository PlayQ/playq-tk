package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.query.DynamoQuery
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.models.{WithActions, WithCheckConditionSuccess}
import d4s.query.predef.transactions.TransactWriteItemAction
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{TransactWriteItemsRequest, TransactWriteItemsResponse}

import scala.jdk.CollectionConverters.*

final case class TransactWrite[+Dec](
  interpreter: QueryInterpreter[TransactWrite, List[TransactWriteItemsResponse]],
  decoder: QueryDecoder[List[TransactWriteItemsResponse], Dec],
  actions: List[TransactWriteItemAction] = Nil,
) extends DynamoQuery[TransactWrite, Dec]
  with WithActions[TransactWriteItemAction, TransactWrite[Dec]]
  with WithCheckConditionSuccess[TransactWrite, Dec] {

  override type Rsp = List[TransactWriteItemsResponse]

  override def withInterpreter(f: QueryInterpreter[TransactWrite, List[TransactWriteItemsResponse]]): TransactWrite[Dec] =
    copy(interpreter = f)

  override def decodeF[C1](f: QueryDecoder[List[TransactWriteItemsResponse], C1]): TransactWrite[C1] =
    copy(decoder = f)

  /** Overwrites action list, subsequent calls do not append. */
  override def withActions(actions: List[TransactWriteItemAction]): TransactWrite[Dec] =
    copy(actions = actions)
}

object TransactWrite {
  def apply(): TransactWrite[List[TransactWriteItemsResponse]] = {
    new TransactWrite(interpreter, QueryDecoder.pure)
  }

  def interpreter: QueryInterpreter[TransactWrite, List[TransactWriteItemsResponse]] = new QueryInterpreter[TransactWrite, List[TransactWriteItemsResponse]] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: TransactWrite[Dec]): F[DynamoException, List[TransactWriteItemsResponse]] = {
      ctx.execute("TransactWriteItemBatch") {
        client =>
          val batches = query.actions.map(_.transactWriteItem).grouped(ctx.transactConfig.writeTransactSize).toList
          F.traverse(batches) {
            requests =>
              val rq = TransactWriteItemsRequest.builder().transactItems(requests.asJava)
              client.executeFuture(_.transactWriteItems(rq.build()))
          }
      }
    }
  }
}
