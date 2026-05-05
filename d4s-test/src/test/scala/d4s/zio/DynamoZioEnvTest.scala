package d4s.zio

import d4s.test.DynamoTestBaseZIO
import d4s.test.Models.*

final class DynamoZioEnvTest extends DynamoTestBaseZIO {
  "Dynamo Zio Env (d4z)" should {
    "perform basic ops" in {
      (testTable: InterpreterTestTable) =>
        val payload = InterpreterTestPayload("perform put2", 321, "f3", RandomPayload("f2"))
        for {
          _     <- d4z.run(testTable.putItem(payload))
          get    = testTable.getItem(payload.key).decodeItem[InterpreterTestPayload]
          read1 <- d4z.run(get)
          _     <- assertIO(read1.contains(payload))
        } yield ()
    }
  }
}
