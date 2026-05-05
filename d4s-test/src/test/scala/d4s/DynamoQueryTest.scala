package d4s

import _root_.zio.IO
import cats.effect.Concurrent
import d4s.DynamoQueryTest.{AdditionalFields, Ctx, ExtendedPayload, NullTestPayload}
import d4s.codecs.{D4SCodec, D4SEncoder, ProjectionExpression, WithD4S}
import d4s.exceptions.DynamoException.QueryException
import d4s.implicits.*
import d4s.query.predef.batches.BatchGetRequestAction.BatchGetRequest
import d4s.query.predef.batches.BatchWriteRequestAction.{BatchDeleteRequest, BatchPutRequest}
import d4s.query.predef.{BatchGet, BatchWrite}
import d4s.table.DynamoTableRef
import d4s.test.DynamoTestBase
import d4s.test.Models.*
import distage.{DefaultModule2, TagKK}
import izumi.functional.bio.{Async2, F, Primitives2}
import logstage.LogIO2
import net.playq.tk.test.rnd.TkRndBase
import org.scalatest.exceptions.TestFailedException
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Random

final class DynamoQueryTest extends DynamoQueryTestBase[IO]

object DynamoQueryTest {

  final case class Ctx[F[+_, +_]](
    dynamo: Dynamo[F],
    testTable: InterpreterTestTable,
    countersTable: TestTable1,
    testTable2: TestTable2,
    testTable3: TestTable3,
    testTable4: TestTable4,
    currentTable: UpdatedTestTable,
    newTable1: UpdatedTestTable1,
    newTable2: UpdatedTestTable2,
    log: LogIO2[F],
  )(implicit
    val C: Concurrent[F[Throwable, _]],
    val P: Primitives2[F],
  )

  final case class NullableField(field4: Option[String])
  object NullableField extends WithD4S[NullableField]

  final case class NullTestPayload(payload: InterpreterTestPayload, nullableField: NullableField)
  object NullTestPayload {
    implicit val codec: D4SCodec.AsObject[NullTestPayload] =
      InterpreterTestPayload.codec.imap2(NullableField.codec)(NullTestPayload(_, _))(ext => ext.payload -> ext.nullableField)
    implicit val attrNames: ProjectionExpression[NullTestPayload] =
      ProjectionExpression[InterpreterTestPayload] ++ ProjectionExpression[NullableField]
  }

  final case class AdditionalFields(field4: String, field5: Int)
  object AdditionalFields extends WithD4S[AdditionalFields]

  final case class ExtendedPayload(payload: InterpreterTestPayload, additionalFields: AdditionalFields)
  object ExtendedPayload {
    implicit val codec: D4SCodec.AsObject[ExtendedPayload] =
      InterpreterTestPayload.codec.imap2(AdditionalFields.codec)(ExtendedPayload(_, _))(ext => ext.payload -> ext.additionalFields)
    implicit val attrNames: ProjectionExpression[ExtendedPayload] =
      ProjectionExpression[InterpreterTestPayload] ++ ProjectionExpression[AdditionalFields]
  }
}

abstract class DynamoQueryTestBase[F[+_, +_]: Async2: TagKK: DefaultModule2] extends DynamoTestBase[F, Ctx[F]] with TkRndBase {

  "DynamoQuery" should {

    "execute PutItem - GetItem" in bound {
      ctx =>
        import ctx.*
        val payload = InterpreterTestPayload("execute put", 12, "f3", RandomPayload("f2"))

        for {
          _     <- dynamo.run(testTable.putItem(payload))
          get    = testTable.getItem(payload.key).decodeItem[InterpreterTestPayload]
          read1 <- dynamo.run(get)
          _     <- assertIO(read1.contains(payload))
        } yield ()
    }

    "execute DeleteItem" in bound {
      ctx =>
        import ctx.*
        val payload = InterpreterTestPayload("execute delete", 22, "f3", RandomPayload("f2"))
        for {
          _     <- dynamo.run(testTable.putItem(payload))
          get    = testTable.getItem(payload.key).decodeItem[InterpreterTestPayload]
          read1 <- dynamo.run(get)
          _     <- assertIO(read1.contains(payload))

          _     <- dynamo.run(testTable.deleteItem(payload.key))
          read2 <- dynamo.run(get)
          _     <- assertIO(read2.isEmpty)
        } yield ()
    }

    "execute UpdateItem" in bound {
      ctx =>
        import ctx.*
        val payload1 = InterpreterTestPayload("execute update", 3, "f3", RandomPayload("f2"))
        val payload2 = InterpreterTestPayload("execute update", 3, "f33", RandomPayload("f5"))
        for {
          _     <- dynamo.run(testTable.putItem(payload1))
          get    = testTable.getItem(payload1.key).decodeItem[InterpreterTestPayload]
          read1 <- dynamo.run(get)
          _     <- assertIO(read1.contains(payload1))

          _     <- dynamo.run(testTable.updateItem(payload2))
          read2 <- dynamo.run(get)
          _     <- assertIO(read2.contains(payload2))

        } yield ()
    }

    "execute PutItem if not exists" in bound {
      ctx =>
        import ctx.*
        val payload1 = InterpreterTestPayload("execute insert if not exists", 4, "f3", RandomPayload("f2"))
        val payload2 = InterpreterTestPayload("execute insert if not exists", 4, "f33", RandomPayload("f5"))
        for {
          _   <- dynamo.run(testTable.putItem(payload1))
          res <- dynamo.run(testTable.putItem.withItem(payload2).ifNotExists().optConditionFailure)
          _   <- assertIO(res.isDefined)

          get    = testTable.getItem(payload1.key).decodeItem[InterpreterTestPayload]
          read1 <- dynamo.run(get)
          _     <- assertIO(read1.contains(payload1))

          _     <- dynamo.run(testTable.putItem(payload2))
          read2 <- dynamo.run(get)
          _     <- assertIO(read2.contains(payload2))
        } yield ()
    }

    "execute DeleteItem if exists" in bound {
      ctx =>
        import ctx.*
        val payload = InterpreterTestPayload("execute delete if exists", 5, "f3", RandomPayload("f2"))
        for {
          _     <- dynamo.run(testTable.putItem(payload))
          get    = testTable.getItem(payload.key).decodeItem[InterpreterTestPayload]
          read1 <- dynamo.run(get)
          _     <- assertIO(read1.contains(payload))

          delete = testTable.deleteItem(payload.key)
          _     <- dynamo.run(delete.ifExists())
          res   <- dynamo.run(delete.ifExists().optConditionFailure)
          _     <- assertIO(res.isDefined)

          read2 <- dynamo.run(get)
          _     <- assertIO(read2.isEmpty)
        } yield ()
    }

    "execute DeleteItem with condition" in bound {
      ctx =>
        import ctx.*

        val payload = InterpreterTestPayload("random string", 5, "prefix-f3", RandomPayload("f2"))

        for {
          _        <- dynamo.run(testTable.putItem(payload))
          get       = testTable.getItem(payload.key).decodeItem[InterpreterTestPayload]
          testRead <- dynamo.run(get)
          _        <- assertIO(testRead.get == payload)

          _ <- dynamo.run {
            testTable
              .deleteItem(payload.key)
              .withCondition("field1".of[String] beginsWith "non random")
              .optConditionFailure
          }

          testRead2 <- dynamo.run(get)
          _         <- assertIO(testRead2.nonEmpty)

          _ <- dynamo.run {
            testTable
              .deleteItem(payload.key)
              .withCondition("field1".of[String] beginsWith "random")
          }

          testRead3 <- dynamo.run(get)
          _         <- assertIO(testRead3.isEmpty)
        } yield ()
    }

    "execute Put/Get/Delete Batch" in bound {
      ctx =>
        import ctx.*
        val randomPayload = RandomPayload("f2")
        val newAll        = (1 to 200).toList.map {
          indx =>
            InterpreterTestPayload("execute put/get/delete batch", indx, "xxx", randomPayload)
        }
        val getOrDeleteBatch = newAll.map(_.key)
        for {
          _     <- dynamo.run(testTable.putItemBatch(newAll))
          scan   = testTable.scan.decodeItems[InterpreterTestPayload].readAll
          read1 <- dynamo.run(scan)
          _     <- assertIO(newAll.forall(el => read1.contains(el)))

          read2 <- dynamo.run(testTable.getItemBatch(getOrDeleteBatch).decodeItems[InterpreterTestPayload])
          _     <- assertIO(newAll.forall(read2.contains))

          _     <- dynamo.run(testTable.deleteItemBatch(getOrDeleteBatch))
          read3 <- dynamo.run(scan)
          _     <- assertIO(newAll.forall(!read3.contains(_)))
        } yield ()
    }

    "execute BatchWrite/BatchGet" in bound {
      ctx =>
        import ctx.*
        val newAll = (1 to 200).toList.map {
          indx =>
            val tableRef = if (Random.nextBoolean()) testTable2.ref else testTable3.ref
            tableRef -> Table1ItemWithCounter(UUID.randomUUID().toString, UUID.randomUUID().toString, indx.toLong)
        }
        val putRequests = newAll.map {
          case (tableRef, item) =>
            BatchPutRequest(tableRef, item)
        }
        def getOrDeleteBatch(tableRef: DynamoTableRef[?]): List[Table1Key] = {
          newAll.collect {
            case (t, x) if t == tableRef =>
              Table1Key(x.key1, x.key2)
          }
        }

        for {
          _     <- dynamo.run(BatchWrite(putRequests))
          doScan = for {
            t2s <- dynamo.run(testTable2.scan.decodeItems[Table1ItemWithCounter].readAll)
            t3s <- dynamo.run(testTable3.scan.decodeItems[Table1ItemWithCounter].readAll)
          } yield t2s ++ t3s

          read1 <- doScan
          _     <- assertIO(read1.nonEmpty)
          _     <- assertIO(read1.size != 1)
          _     <- assertIO(read1.size == newAll.size)
          _     <- F.traverse(newAll) { case (_, el) => assertIO(read1.contains(el)) }

          read2 <- dynamo.run(
            BatchGet(testTable2.ref, BatchGetRequest(getOrDeleteBatch(testTable2.ref)).decodeItems[Table1ItemWithCounter])
              .withAction(testTable3.ref, BatchGetRequest(getOrDeleteBatch(testTable3.ref)).decodeItems[Table1ItemWithCounter])
          )
          _ <- F.traverse(newAll) { case (_, el) => assertIO(read2.contains(el)) }

          _ <- dynamo.run(
            BatchWrite(
              getOrDeleteBatch(testTable2.ref).map(BatchDeleteRequest(testTable2.ref, _))
                ++ getOrDeleteBatch(testTable3.ref).map(BatchDeleteRequest(testTable3.ref, _))
            )
          )
          read3 <- doScan
          _     <- F.traverse(newAll) { case (_, el) => assertIO(!read3.contains(el)) }
        } yield ()
    }

    "execute UpdateItem with condition" in bound {
      ctx =>
        import ctx.*
        val payload1         = InterpreterTestPayload("execute update with condition", 9, "f3", RandomPayload("f22"))
        val modifiedPayload1 = InterpreterTestPayload("execute update with condition", 9, "f33", RandomPayload("3123"))
        for {
          _ <- dynamo.run(testTable.putItem(payload1))

          res <- dynamo.run(testTable.updateItem(modifiedPayload1).withCondition("field3".of[String] === "f7").optConditionFailure)
          _   <- assertIO(res.isDefined)

          _    <- dynamo.run(testTable.updateItem(modifiedPayload1).withCondition("field3".of[String] === "f3"))
          read <- dynamo.run(testTable.scan.decodeItems[InterpreterTestPayload].readAll)
          _    <- assertIO(read.contains(modifiedPayload1))
        } yield ()
    }

    "execute UpdateItem with null and not null condition" in bound {
      ctx =>
        import ctx.*
        val payload1 = InterpreterTestPayload("execute update with null condition", 9, "f3", RandomPayload("f22"))
        for {
          _ <- dynamo.run(testTable.putItem(payload1))

          _ <- dynamo.run(
            testTable
              .updateItem(payload1.key)
              .withCondition("p".notNull)
              .withUpdateExpression("SET field4 = :nullField")
              .withAttributeValues(":nullField" -> AttributeValue.builder().nul(true).build())
          )

          _ <- dynamo.run(
            testTable
              .updateItem(payload1.key)
              .withCondition("field4".isNull)
              .withUpdateExpression("SET field4 = :notnullField")
              .withAttributeValues(":notnullField" -> D4SEncoder.encode(Some("not null"): Option[String]))
          )
          read <- dynamo.run(testTable.scan.decodeItems[NullTestPayload].readAll)
          _    <- assertIO(read.exists(_.nullableField.field4.contains("not null")))
        } yield ()
    }

    "execute UpdateItem with complex condition" in bound {
      ctx =>
        import ctx.*

        val payload1         = InterpreterTestPayload("execute update with complex condition", 3, "f3", RandomPayload("f22"))
        val modifiedPayload1 = InterpreterTestPayload("execute update with complex condition", 3, "f33", RandomPayload("3123"))

        val complexCondition1 = "field2".of[Int].between(2, 4) && ("field3".of[String] === "f4" || "field3".of[String] === "f12")
        val complexCondition2 = "field2".of[Int].between(2, 4) && ("field3".of[String] === "f3" || "field3".of[String] === "f12")

        for {
          _ <- dynamo.run(testTable.putItem(payload1))

          res <- dynamo.run(testTable.updateItem(modifiedPayload1).withCondition(complexCondition1).optConditionFailure)
          _   <- assertIO(res.isDefined)

          _    <- dynamo.run(testTable.updateItem(modifiedPayload1).withCondition(complexCondition2))
          read <- dynamo.run(testTable.scan.decodeItems[InterpreterTestPayload].readAll)
          _    <- assertIO(read.contains(modifiedPayload1))
        } yield ()
    }

    "execute UpdateItem with updateExpression" in bound {
      ctx =>
        import ctx.*

        val key              = InterpreterTestKey("execute update with updateExpression", 3)
        val payload          = InterpreterTestPayload(key)("f3", RandomPayload("f22"))
        val modifiedPayload1 = InterpreterTestPayload(key)("f3", RandomPayload("asdf456456"))

        // noinspection DoubleNegationScala,PostfixUnaryOperation
        val complexCondition = "field2".of[Int] < 5 && (!"field3".existsField.unary_! && "field4".notExists) && !("field3".of[String] < "f12")

        for {
          _ <- log.info(s"${complexCondition.eval().condition.conditionExpression -> "conditionExpression"}")

          _ <- dynamo.run(testTable.putItem(payload))

          _ <- dynamo.run {
            testTable
              .updateItem(modifiedPayload1)
              .withCondition(complexCondition)
              .withUpdateExpression("SET field4 = :field4, field5 = :field5 REMOVE field9")
              .withAttributeValues(":field4" -> D4SEncoder.encode("FIELD4"))
              .withAttributeValues(":field5" -> D4SEncoder.encode(8))
              .optConditionFailure
          }.flatMap(failure => F.sync(assert(failure.isEmpty)))

          _ <- dynamo.run {
            testTable.updateItem(modifiedPayload1).withCondition(complexCondition).optConditionFailure
          }.flatMap(failure => F.sync(failure.isDefined))

          item1 <- dynamo.run(testTable.getItem(key).decodeItem[ExtendedPayload])
          _     <- assertIO(item1.contains(ExtendedPayload(modifiedPayload1, AdditionalFields("FIELD4", 8))))

          _ <- dynamo.run {
            testTable
              .updateItem(key)
              .withUpdateExpression("SET field4 = :field4")
              .withAttributeValues(":field4" -> D4SEncoder.encode("X"))
              .optConditionFailure
          }.flatMap(failure => F.sync(failure.isEmpty))

          item2 <- dynamo.run(testTable.getItem(key).decodeItem[ExtendedPayload])
          _     <- assertIO(item2.contains(ExtendedPayload(modifiedPayload1, AdditionalFields("X", 8))))
        } yield ()
    }

    "execute Query" in bound {
      ctx =>
        import ctx.*
        val hash     = "ranges read-delete"
        val range1   = 111
        val range2   = 122
        val payload1 = InterpreterTestPayload(hash, range1, "f3", RandomPayload("f2"))
        val payload2 = InterpreterTestPayload(hash, range2, "f3", RandomPayload("f2"))

        for {
          _ <- dynamo.run(testTable.putItem(payload1))

          query = testTable.query.withKey(Map("field1" -> D4SEncoder.encode(hash))).decodeItems[InterpreterTestPayload].readAll
          res1 <- dynamo.run(query)
          _    <- assertIO(res1.size == 1)
          _    <- dynamo.run(testTable.putItem(payload2))

          res2 <- dynamo.run(query)
          _    <- assertIO(res2.size == 2)

          _    <- dynamo.run(testTable.deleteItem(payload1.key))
          res3 <- dynamo.run(query)
          _    <- assertIO(res3.size == 1)

          _    <- dynamo.run(testTable.deleteItem(payload2.key))
          res4 <- dynamo.run(query)
          _    <- assertIO(res4.isEmpty)
        } yield ()
    }

    "execute Query on index" in bound {
      ctx =>
        import ctx.*
        val payload = InterpreterTestPayload("index_test", 333, "f3", RandomPayload("f2"))
        for {
          _  <- dynamo.run(testTable.putItem(payload))
          get = testTable.query
            .withIndex(testTable.globalIndex)
            .withKey(testTable.globalIndex.key.bind("index_test", "f3"))
            .decodeItems[InterpreterTestPayload]
            .readAll
          read1 <- dynamo.run(get)
          _     <- assertIO(read1.size == 1)
          _     <- assertIO(read1.head == payload)
        } yield ()
    }

    "execute Query with conditions" in bound {
      ctx =>
        import ctx.*
        val payload = InterpreterTestPayload("query_conditions_test", 333, "f3", RandomPayload("f2"))
        for {
          _   <- dynamo.run(testTable.putItem(payload))
          get1 = testTable.query
            .withKey(testTable.key.bind("query_conditions_test"))
            .withCondition("field2".of[Int] > 332)
            .decodeItems[InterpreterTestPayload]
            .readAll
          read1 <- dynamo.run(get1)
          _     <- assertIO(read1.size == 1)
          _     <- assertIO(read1.head == payload)

          get2 = testTable.query
            .withKey(testTable.key.bind("query_conditions_test"))
            .withCondition("field2".of[Int] < 332)
            .decodeItems[InterpreterTestPayload]
            .readAll
          read2 <- dynamo.run(get2)
          _     <- assertIO(read2.isEmpty)
        } yield ()
    }

    "execute Query read with limit" in bound {
      ctx =>
        import ctx.*

        val payload      = InterpreterTestPayload("paging_test", 333, "f3", RandomPayload("f2"))
        val prefix       = UUID.randomUUID()
        val put          = testTable.putItem.prefixed(prefix)
        val expectedSize = 100
        for {
          _ <- F.parTraverseN(3)((1 to expectedSize).toList) {
            i =>
              dynamo.run(put.withItem(payload.copy(field2 = i)))
          }
          read1 <- dynamo.run {
            testTable.query
              .withKey(testTable.key.bind("paging_test"))
              .prefixed(prefix)
              .decodeItems[InterpreterTestPayload]
              .withPageSize(10)(using d4s.unsafe.unsafeDynamoOps) // note this (per-page) limit contradicts global limit
              .readLimit(expectedSize)
          }
          _ <- assertIO(read1.size == expectedSize)
        } yield ()
    }

    "execute Query read with offsets" in bound {
      ctx =>
        import ctx.*
        import d4s.unsafe.unsafeDynamoOps

        val payload      = InterpreterTestPayload("offset_test", 0, "x", RandomPayload("y"))
        val prefix       = UUID.randomUUID()
        val put          = testTable.putItem.prefixed(prefix)
        val expectedSize = 20

        for {
          _ <- F.parTraverseN(3)((1 to expectedSize).toList) {
            i =>
              dynamo.run {
                put.withItem(payload.copy(field2 = i)).prefixed(prefix)
              }
          }
          query1 = testTable.query
            .withKey(testTable.key.bind("offset_test"))
            .decodeItems[InterpreterTestPayload]
            .prefixed(prefix)
            .readOffsetLimit(13, 5)
          range13_5 <- dynamo.run(query1)
          _         <- assertIO(range13_5.size == 5)
          _         <- assertIO(range13_5.map(_.field2) == List(14, 15, 16, 17, 18))

          query2 = testTable.query
            .withKey(testTable.key.bind("offset_test"))
            .decodeItems[InterpreterTestPayload]
            .prefixed(prefix)
            .readOffsetLimit(35, 5)
          range35_5 <- dynamo.run(query2)
          _         <- assertIO(range35_5.isEmpty)

          query3 = testTable.query
            .withKey(testTable.key.bind("offset_test"))
            .decodeItems[InterpreterTestPayload]
            .prefixed(prefix)
            .readOffsetLimit(1, 3)
          range1_3 <- dynamo.run(query3)
          _        <- assertIO(range1_3.size == 3)
          _        <- assertIO(range1_3.map(_.field2) == List(2, 3, 4))

          query4 = testTable.query
            .withKey(testTable.key.bind("offset_test"))
            .decodeItems[InterpreterTestPayload]
            .prefixed(prefix)
            .readOffsetLimit(0, 3)
          range0_3 <- dynamo.run(query4)
          _        <- assertIO(range0_3.size == 3)
          _        <- assertIO(range0_3.map(_.field2) == List(1, 2, 3))

          query5 = testTable.query
            .withKey(testTable.key.bind("offset_test"))
            .decodeItems[InterpreterTestPayload]
            .prefixed(prefix)
            .readOffsetLimit(-5, 3)
          exception <- dynamo.run(query5).asBad[QueryException]
          _         <- assertIO(exception.getMessage contains "Negative offset")

          query6 = testTable.query
            .withKey(testTable.key.bind("offset_test"))
            .decodeItems[InterpreterTestPayload]
            .prefixed(prefix)
            .readPage
          _ <- dynamo.run(query6)
        } yield ()
    }

    "IsSafeQuery implicit prohibits unpaged queries" in bound {
      ctx =>
        val _ = ctx
        for {
          error1 <- F.syncThrowable(intercept[TestFailedException](assertCompiles {
            "ctx.dynamo.run(ctx.testTable.query)"
          }))
          _      <- assertIO(error1.getMessage.contains("WithPagesOffset"))
          _      <- assertIO(error1.getMessage.contains("Please construct a valid query"))
          error2 <- F.syncThrowable(intercept[TestFailedException](assertCompiles {
            "ctx.dynamo.run(d4s.query.predef.ListTables())"
          }))
          _ <- assertIO(error2.getMessage.contains("WithPages"))
          _ <- assertIO(!error2.getMessage.contains("WithPagesOffset"))
          _ <- assertIO(error2.getMessage.contains("Please construct a valid query"))
        } yield ()
    }

    "execute PutItemBatch with prefix" in bound {
      ctx =>
        import ctx.*
        val payload      = InterpreterTestPayload("batch_test", 333, "f3", RandomPayload("f2"))
        val prefix       = UUID.randomUUID()
        val expectedSize = 100
        for {
          items <- F.sync((1 to expectedSize).map(i => payload.copy(field2 = i)).toList)
          put    = testTable.putItemBatch(items).prefixed(prefix)
          _     <- dynamo.run(put)

          get = testTable.query
            .withKey(testTable.key.bind("batch_test"))
            .prefixed(prefix)
            .decodeItems[InterpreterTestPayload]
            .readAll

          read1 <- dynamo.run(get)
          _     <- assertIO(read1.size == expectedSize)
          _     <- assertIO(read1.toSet == items.toSet)

          delete = testTable
            .deleteItemBatch(items.map(_.key))
            .prefixed(prefix)

          _ <- dynamo.run(delete)

          read2 <- dynamo.run(get)
          _     <- assertIO(read2.isEmpty)
        } yield ()
    }

    "execute Describe/Update Tags" in bound {
      ctx =>
        import ctx.*
        for {
          arn <- dynamo.run(testTable.describe).map(_.table().tableArn())
          _   <- dynamo.run(testTable.updateTags(arn, Map("someTags" -> "test")))
          _   <- dynamo.run(testTable.markForDeletion(arn))
        } yield ()
    }

    "execute UpdateItem with counter updates" in bound {
      ctx =>
        import ctx.*
        val v1     = UUID.randomUUID().toString
        val v2     = UUID.randomUUID().toString
        val prefix = UUID.randomUUID()

        for {
          _ <- dynamo.run("add value") {
            countersTable
              .updateItem(Table1Key(v1, v2))
              .withUpdateExpression("ADD counterField :value")
              .withAttributeValues(":value" -> D4SEncoder.encode(0L))
              .prefixed(prefix)
          }

          _ <- dynamo.run("add value") {
            countersTable
              .updateItem(Table1Key(v1, v2))
              .prefixed(prefix)
              .withUpdateExpression("ADD counterField :value")
              .withAttributeValues(":value" -> D4SEncoder.encode(1L))
          }

          res <- dynamo.run("get value") {
            countersTable
              .getItem(Table1Key(v1, v2))
              .prefixed(prefix)
              .decodeItem[Table1ItemWithCounter]
              .map(_.map(_.counterField).getOrElse(-1L))
          }

          _ <- assertIO(res == 1L)
        } yield ()
    }

    "execute UpdateTable" in bound {
      ctx =>
        import ctx.*

        for {
          _ <- dynamo.run("update-index") {
            currentTable.update.withIndexToCreate(newTable1.globalIndex.toProvisionedIndex(newTable1.provisioning))
          }
          res <- dynamo.run("describe")(currentTable.describe)
          set  = Set("key1", "key2", "key3")
          _   <- assertIO(res.table().globalSecondaryIndexes().asScala.nonEmpty)
          _   <- assertIO(res.table().globalSecondaryIndexes().asScala.size == 1)
          _   <- assertIO(res.table().attributeDefinitions().asScala.map(_.attributeName()).forall(set.contains))

          _ <- dynamo.run("update") {
            currentTable.update
              .withIndexToCreate(newTable2.globalIndex2.toProvisionedIndex(newTable2.provisioning))
              .retryOnFailure(10, 3.seconds)(_ => true)
          }

          res2 <- dynamo.run("describe-2")(currentTable.describe)
          set2  = Set("key1", "key2", "key3", "key4")
          _    <- assertIO(res2.table().globalSecondaryIndexes().asScala.nonEmpty)
          _    <- assertIO(res2.table().globalSecondaryIndexes().asScala.size == 2)
          _    <- assertIO(res2.table().attributeDefinitions().asScala.map(_.attributeName()).forall(set2.contains))
        } yield ()
    }

    "execute Query stream" in bound {
      ctx =>
        import ctx.*
        val payload      = InterpreterTestPayload("streaming_test", 333, "f3", RandomPayload("f2"))
        val prefix       = UUID.randomUUID()
        val put          = testTable.putItem.prefixed(prefix)
        val expectedSize = 100
        for {
          expectedElems <- F.parTraverseN(3)((1 to expectedSize).toList) {
            i =>
              val elem = payload.copy(field2 = i)
              dynamo.run(put.withItem(elem)).as(elem)
          }
          ref <- F.mkRef(Set.empty[InterpreterTestPayload])
          get  = testTable.query
            .withKey(testTable.key.bind("streaming_test"))
            .withPageSize(10)(using d4s.unsafe.unsafeDynamoOps)
            .prefixed(prefix)
            .decodeItems[InterpreterTestPayload]
            .stream
          all <- dynamo
            .stream(get)
            .evalTap(res => ref.update(_ + res).void)
            .covary[F[Throwable, _]].compile.toList
          counted <- ref.get
          _       <- assertIO(counted.size == expectedSize)
          _       <- assertIO(all.toSet == expectedElems.toSet)

          get2OnUncreatedTable = testTable.query
            .withKey(testTable.key.bind("streaming_test"))
            .prefixed(UUID.randomUUID())
            .decodeItems[InterpreterTestPayload]
            .stream
          none <- dynamo.stream(get2OnUncreatedTable).covary[F[Throwable, _]].compile.toList
          _    <- assertIO(none.isEmpty)
        } yield ()
    }

    "execute QueryDeleteBatch" in bound {
      ctx =>
        import ctx.*
        val payload      = InterpreterTestPayload("batch_test", 333, "f3", RandomPayload("f2"))
        val prefix       = UUID.randomUUID()
        val expectedSize = 100
        val items        = (1 to expectedSize).map(i => payload.copy(field2 = i)).toList
        for {
          put <- F.pure(testTable.putItemBatch(items).prefixed(prefix))
          _   <- dynamo.run(put)

          get = testTable.query
            .withKey(testTable.key.bind("batch_test"))
            .decodeItems[InterpreterTestPayload]
            .prefixed(prefix)
            .readAll

          read1 <- dynamo.run(get)
          _     <- assertIO(read1.size == expectedSize)
          _     <- assertIO(read1.toSet == items.toSet)

          deleteQuery = testTable
            .queryDeleteBatch(testTable.key.bind("batch_test"))
            .prefixed(prefix)
          _ <- dynamo.run(deleteQuery)

          read2 <- dynamo.run(get)
          _     <- assertIO(read2.isEmpty)

          _ <- dynamo.run(put)

          read3 <- dynamo.run(get)
          _     <- assertIO(read3.size == expectedSize)
          _     <- assertIO(read3.toSet == items.toSet)

          deleteScan = testTable.scanDeleteBatch.prefixed(prefix)
          _         <- dynamo.run(deleteScan)

          read4 <- dynamo.run(get)
          _     <- assertIO(read4.isEmpty)
        } yield ()
    }

    "execute QueryDeleteBatch with prefix" in bound {
      ctx =>
        import ctx.*
        val prefix1 = UUID.randomUUID()
        val prefix2 = UUID.randomUUID()

        for {
          _ <- dynamo.run(testTable.queryDeleteBatch(testTable.key.bind("batch_test")).prefixed(prefix1))
          _ <- dynamo.run(testTable.scanDeleteBatch.prefixed(prefix2))
        } yield ()
    }

    "preform Query with in set filters" in bound {
      ctx =>
        import ctx.*
        val payload = InterpreterTestPayload("number_test", 333, "f4", RandomPayload("f2"))
        for {
          _     <- dynamo.run(testTable.putItem(payload))
          read1 <- dynamo.run {
            testTable
              .query(testTable.globalIndex, "number_test", "f4")
              .withFilterExpression(List("p", "randomArray").of[List[Int]] contains payload.p.randomArray.head)
              .withFilterExpression(List("p", "field1").of[String] contains "f2")
              .decodeItems[InterpreterTestPayload]
              .readAll
          }
          _     <- assertIO(read1.size == 1)
          _     <- assertIO(read1.head == payload)
          read2 <- dynamo.run {
            testTable
              .query(testTable.globalIndex, "number_test", "f4")
              .withFilterExpression(!(List("p", "randomArray").of[List[Int]] contains payload.p.randomArray.head))
              .decodeItems[InterpreterTestPayload]
              .readAll
          }
          _     <- assertIO(read2.isEmpty)
          read3 <- dynamo.run {
            testTable
              .query(testTable.globalIndex, "number_test", "f4")
              .withFilterExpression(List("p", "field1").of[String] contains "2")
              .decodeItems[InterpreterTestPayload]
              .readAll
          }
          _     <- assertIO(read3.size == 1)
          _     <- assertIO(read3.head == payload)
          read4 <- dynamo.run {
            testTable
              .query(testTable.globalIndex, "number_test", "f4")
              .withFilterExpression(List("p", "field1").of[String] contains "234")
              .decodeItems[InterpreterTestPayload]
              .readAll
          }
          _ <- assertIO(read4.isEmpty)
        } yield ()
    }

    "execute queries with literal tuples" in bound {
      ctx =>
        import ctx.*
        for {
          key   <- F.random[Int].map(i => s"key$i")
          _     <- dynamo.run(testTable4.putItem.withItem(("key1", key)).withItem(("value1", "f3")))
          get    = testTable4.getItem(("key1", key)).decodeItem[("value1", String)]
          read1 <- dynamo.run(get)
          _     <- assertIO(read1 contains ("value1" -> "f3"))
        } yield ()
    }
  }
}
