package d4s.zio

import d4s.Dynamo3
import zio.ZIO

object d4z
  extends DynamoZioEnv[ZIO](
    new DynamoZioEnv.WithDynamo[ZIO] {
      override def apply[R, E, A](f: Dynamo3[ZIO] => ZIO[R, E, A]): ZIO[Dynamo3[ZIO] & R, E, A] = {
        ZIO.service[Dynamo3[ZIO]].flatMap(f)
      }
    }
  )
