package d4s.test

import d4s.codecs.{D4SCodec, WithD4S}
import d4s.config.DynamoMeta
import d4s.table.{DynamoKey, DynamoTableDef}
import d4s.table.index.GlobalIndex
import net.playq.tk.aws.common.ServiceName

import java.util.UUID
import scala.annotation.unused
import scala.util.Random

object Models {
  private def testTableName   = s"interpreter-${UUID.randomUUID().toString.take(5)}"
  private def testServiceName = ServiceName("test-service")

  final case class Table1Key(key1: String, key2: String)
  object Table1Key extends WithD4S[Table1Key]

  final case class Table1Item(v1: String, v2: String)
  object Table1Item extends WithD4S[Table1Item]

  final case class Table1ItemWithCounter(key1: String, key2: String, counterField: Long)
  object Table1ItemWithCounter extends WithD4S[Table1ItemWithCounter]

  final class TestTable1(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
    override val name: String                   = "table1"
    override val serviceName: ServiceName       = testServiceName
    override val key: DynamoKey[String, String] = DynamoKey("key1", "key2")
    override val ttlField: Option[String]       = None
  }

  final class TestTable2(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
    override val name: String                   = "table2"
    override val serviceName: ServiceName       = testServiceName
    override val key: DynamoKey[String, String] = DynamoKey("key1", "key2")
    override val ttlField: Option[String]       = None
  }

  final class TestTable3(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
    override val name: String                   = "table3"
    override val serviceName: ServiceName       = testServiceName
    override val key: DynamoKey[String, String] = DynamoKey("key1", "key2")
    override val ttlField: Option[String]       = None
  }

  final class TestTable4(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
    override val name: String                    = "table4"
    override val serviceName: ServiceName        = ServiceName("test-service")
    override val key: DynamoKey[String, Nothing] = DynamoKey("key1")
    override val ttlField: Option[String]        = None
  }

  final class UpdatedTestTable(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
    override val name: String                   = "table_update"
    override val serviceName: ServiceName       = testServiceName
    override val key: DynamoKey[String, String] = DynamoKey("key1", "key2")
    override val ttlField: Option[String]       = None
  }

  final class UpdatedTestTable1(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
    override val name: String                   = "table_update"
    override val serviceName: ServiceName       = testServiceName
    override val key: DynamoKey[String, String] = DynamoKey("key1", "key2")
    override val ttlField: Option[String]       = None

    val globalIndex: GlobalIndex[String, String] = makeGlobalIndex(
      "key3-to-key1",
      DynamoKey[String, String]("key1", "key3"),
    )
  }

  final class UpdatedTestTable2(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
    override val name: String                   = "table_update"
    override val serviceName: ServiceName       = testServiceName
    override val key: DynamoKey[String, String] = DynamoKey("key1", "key2")
    override val ttlField: Option[String]       = None

    val globalIndex: GlobalIndex[String, String] = makeGlobalIndex(
      "key3-to-key1",
      DynamoKey[String, String]("key1", "key3"),
    )

    val globalIndex2: GlobalIndex[String, String] = makeGlobalIndex(
      "key4-to-key2",
      DynamoKey[String, String]("key2", "key4"),
    )
  }

  final class InterpreterTestTable(implicit dynamoMeta: DynamoMeta) extends DynamoTableDef {
    override val name: String                = testTableName
    override val serviceName: ServiceName    = testServiceName
    override val key: DynamoKey[String, Int] = DynamoKey[String, Int]("field1", "field2")
    override val ttlField: Option[String]    = None

    val globalIndex: GlobalIndex[String, String] = makeGlobalIndex(
      "field1-to-field3",
      DynamoKey[String, String]("field1", "field3"),
    )
  }

  final case class InterpreterTestKey(field1: String, field2: Int)
  object InterpreterTestKey extends WithD4S[InterpreterTestKey]

  final case class InterpreterTestPayload(field1: String, field2: Int, field3: String, p: RandomPayload) {
    def key: InterpreterTestKey = InterpreterTestKey(field1, field2)
  }
  object InterpreterTestPayload extends WithD4S[InterpreterTestPayload] {
    def apply(field1: String, field2: Int)(field3: String, p: RandomPayload)(implicit @unused d: DummyImplicit): InterpreterTestPayload = {
      new InterpreterTestPayload(field1, field2, field3, p)
    }
    def apply(key: InterpreterTestKey)(field3: String, p: RandomPayload): InterpreterTestPayload = {
      new InterpreterTestPayload(key.field1, key.field2, field3, p)
    }
  }

  final case class RandomPayload(field1: String, randomArray: Set[Int])
  object RandomPayload {
    def apply(field1: String): RandomPayload             = RandomPayload(field1, List.fill(10)(Random.nextInt(10)).toSet)
    implicit val codec: D4SCodec.AsObject[RandomPayload] = D4SCodec.AsObject.derived[RandomPayload]
  }
}
