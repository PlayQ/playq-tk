package d4s.query.predef

import d4s.exceptions.DynamoException
import d4s.exceptions.DynamoException.DecoderException
import d4s.query.DynamoQuery
import d4s.query.predef.batches.BatchGetRequestAction.IndividualBatchGetItem
import d4s.query.effect.QueryInterpreter.batchLoop
import d4s.query.effect.{QueryCtx, QueryDecoder, QueryInterpreter}
import d4s.query.predef.batches.BatchGetRequestAction
import d4s.table.DynamoTableRef
import izumi.functional.bio.{F, IO2, Temporal2}
import software.amazon.awssdk.services.dynamodb.model.{BatchGetItemRequest, BatchGetItemResponse}

import scala.jdk.CollectionConverters.*

final class BatchGet[+Dec] private (
  val interpreter: QueryInterpreter[BatchGet, List[BatchGetItemResponse]],
  val decoder: QueryDecoder[List[BatchGetItemResponse], Dec],
  val actions: Map[DynamoTableRef[Any], BatchGetRequestAction[Dec]],
) extends DynamoQuery[BatchGet, Dec] {

  override type Rsp = List[BatchGetItemResponse]

  override def withInterpreter(f: QueryInterpreter[BatchGet, List[BatchGetItemResponse]]): BatchGet[Dec] =
    new BatchGet[Dec](interpreter, decoder, actions)

  override def decodeF[C1](f: QueryDecoder[List[BatchGetItemResponse], C1]): BatchGet[C1] = new BatchGet[C1](
    interpreter = interpreter,
    decoder     = f,
    actions     = actions.map {
      case (tableRef, r) =>
        tableRef -> new BatchGetRequestAction.Raw[C1](
          r.individualGetItems,
          f.contramap(objs => List(BatchGetItemResponse.builder().responses(Map(tableRef.tableName -> objs.map(_.asJava).asJava).asJava).build())),
        )
    },
  )

  def withActions[D](actions: Map[DynamoTableRef[?], BatchGetRequestAction[List[D]]]): BatchGet[List[D]] = {
    new BatchGet[List[D]](interpreter, BatchGet.decoder(actions), actions)
  }

  private def requestBuilder: BatchGetItemRequest.Builder = {
    BatchGetItemRequest.builder()
  }

  private def requestItems: Map[String, List[IndividualBatchGetItem]] = {
    actions.map(r => r._1.tableName -> r._2.individualGetItems(r._1))
  }

}

object BatchGet {
  def apply(): BatchGet[List[BatchGetItemResponse]]                                                        = new BatchGet(interpreter, QueryDecoder.pure, Map.empty)
  def apply[Dec](actions: Map[DynamoTableRef[Any], BatchGetRequestAction[List[Dec]]]): BatchGet[List[Dec]] =
    new BatchGet[List[Dec]](interpreter, decoder(actions), actions)
  def apply[Dec](tableRef: DynamoTableRef[Any], getRequest: BatchGetRequestAction[List[Dec]]): BatchGet[List[Dec]] = apply[Dec](Map(tableRef -> getRequest))

  implicit class WithAction[Dec](private val query: BatchGet[List[Dec]]) extends AnyVal {
    def withAction[D >: Dec](tableRef: DynamoTableRef[?], getRequest: BatchGetRequestAction[List[D]]): BatchGet[List[D]] = {
      val newActions = query.actions + (tableRef -> getRequest)
      new BatchGet[List[D]](interpreter, decoder[D](newActions), newActions)
    }
  }

  implicit class WithActionInitial(private val query: BatchGet[List[BatchGetItemResponse]]) extends AnyVal {
    def withAction[Dec](tableRef: DynamoTableRef[?], getRequest: BatchGetRequestAction[List[Dec]]): BatchGet[List[Dec]] = {
      val newActions: Map[DynamoTableRef[?], BatchGetRequestAction[List[Dec]]] = Map(tableRef -> getRequest)
      new BatchGet[List[Dec]](interpreter, decoder[Dec](newActions), newActions)
    }
  }

  def decoder[Dec](actions: Map[DynamoTableRef[?], BatchGetRequestAction[List[Dec]]]): QueryDecoder[List[BatchGetItemResponse], List[Dec]] = {
    new QueryDecoder[List[BatchGetItemResponse], List[Dec]] {
      override def decode[F[+_, +_]: IO2](rsp: Either[DynamoException, List[BatchGetItemResponse]]): F[DynamoException, List[Dec]] = {
        for {
          batchGetItemResponses <- F.fromEither(rsp)
          listDec               <- F.flatTraverse(batchGetItemResponses) {
            rsp =>
              F.flatTraverse(rsp.responses().asScala) {
                case (tableName, items) =>
                  for {
                    action <- actions.find(_._1.tableName == tableName) match {
                      case Some((_, action)) => F.pure(action)
                      case None              => F.fail(new DecoderException(s"Couldn't find decoder associated with table `$tableName` in BatchGet", None))
                    }
                    res <- action.decoder.decode(Right(items.asScala.toList.map(_.asScala.toMap)))
                  } yield res
              }
          }
        } yield listDec
      }
    }
  }

  def interpreter: QueryInterpreter[BatchGet, List[BatchGetItemResponse]] = {
    BatchGet.executeGetBatch[BatchGet]("BatchGet")(
      requestBuilder = _.requestBuilder,
      requestItems   = _.requestItems,
    )
  }

  @inline def executeGetBatch[DQ[+_]](
    operation: String
  )(requestBuilder: DQ[Any] => BatchGetItemRequest.Builder,
    requestItems: DQ[Any] => Map[String, List[IndividualBatchGetItem]],
  ): QueryInterpreter[DQ, List[BatchGetItemResponse]] = new QueryInterpreter[DQ, List[BatchGetItemResponse]] {
    override def interpret[F[+_, +_]: IO2: Temporal2, Dec](ctx: QueryCtx[F])(query: DQ[Dec]): F[DynamoException, List[BatchGetItemResponse]] = {
      val rqItems = requestItems(query)
      ctx.execute(operation, rqItems.keysIterator.toList) {
        client =>
          val batches = rqItems.values.toList.flatten.grouped(ctx.batchConfig.getBatchSize).toList
          F.traverse(batches) {
            items =>
              batchLoop(
                rq0         = requestBuilder(query).requestItems(IndividualBatchGetItem.toKeysAndAttributes(items).asJava).build(),
                batchConfig = ctx.batchConfig,
              )(interpret = request => client.executeFuture(_.batchGetItem(request)))(
                getUnprocessedKeys = _.unprocessedKeys()
              )(mkNewRq = _.toBuilder.requestItems(_).build())
          }
      }
    }
  }

}
