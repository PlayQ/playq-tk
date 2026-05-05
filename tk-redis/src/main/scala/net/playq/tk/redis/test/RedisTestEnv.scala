package net.playq.tk.redis.test

import distage.TagKK
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.distage.model.reflection.DIKey
import izumi.distage.testkit.model.TestConfig.PriorAxisDIKeys
import net.playq.tk.redis.RedisComponent
import net.playq.tk.test.{MemoizationRoots, WithProduction}

trait RedisTestEnv[F[+_, +_]] extends MemoizationRoots with WithProduction {
  implicit val tagBIO: TagKK[F]

  abstract override def memoizationRoots: PriorAxisDIKeys =
    super.memoizationRoots ++
    Map(0 -> Map(Scene.Managed -> DIKey[RedisComponent[F]]))
}
